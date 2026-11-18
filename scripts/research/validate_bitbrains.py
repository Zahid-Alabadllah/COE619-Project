"""First independent-provider validation of frozen forecasts, no model tuning."""
from pathlib import Path, PurePosixPath
from collections import defaultdict
from datetime import datetime, timezone
import csv
import argparse
import gzip
import hashlib
import io
import json
import re
import sys
import zipfile
from urllib.request import urlretrieve
sys.dont_write_bytecode=True
from validate_complete_sequences import CODE, evaluate

ROOT=CODE/'results/predictor_investigation/bitbrains_holdout'
DIGEST='11313f528a0cbcbe57e63162f8ae5a41a9c7e7c1a79872e294ff3c5bbaa2e671'


def main():
    global ROOT
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--label',default='bitbrains_holdout')
    parser.add_argument('--skip-files',type=int,default=0)
    parser.add_argument('--freeze-plan',action='store_true')
    args=parser.parse_args()
    assert re.fullmatch('[a-z0-9_]+',args.label) and args.skip_files>=0
    ROOT=CODE/'results/predictor_investigation'/args.label
    ROOT.mkdir(parents=True,exist_ok=True)
    if args.freeze_plan:
        assert not (ROOT/'plan.json').exists(),'Do not overwrite an earlier frozen plan'
        (ROOT/'plan.json').write_text(json.dumps(dict(recorded_utc=datetime.now(timezone.utc).isoformat(),
            source='https://atlarge-research.com/gwa-t-12/',archive_sha256=DIGEST,skip_numeric_files=args.skip_files,
            selection='First76 eligible VMs within the next128 numeric files, earliest common complete UTC day; time/completeness only',
            primary_candidate='three_way_mae: select persistence/adaptive trend/mean20 by last20 earlier absolute errors, min10, persistence warmup/tie preference',
            secondary='three_way_mse is a predeclared squared-error sensitivity check, not selected after validation',
            frozen_settings='All windows20 except10-sample time trend; no model or hyperparameter updates after these VM outcomes',
            attribution='Bitbrains IT Services; Grid Workloads Archive; Shen, van Beek and Iosup, CCGrid2015'),indent=2)+'\n')
    assert (ROOT/'plan.json').exists(),'Freeze selection/model plan before reading outcomes'
    assert not (ROOT/'results.json').exists(),'Do not overwrite validation outcomes'
    archive=CODE/'target/bitbrains-fastStorage.zip'
    if not archive.exists():
        archive.parent.mkdir(parents=True,exist_ok=True)
        urlretrieve('https://atlarge-research.com/gwa-traces/gwa_t_12_fastStorage.zip',archive)
    assert hashlib.sha256(archive.read_bytes()).hexdigest()==DIGEST
    series={};file_hashes={};units={};timesteps={};rejected=defaultdict(int)
    with zipfile.ZipFile(archive) as source:
        names=sorted((n for n in source.namelist() if n.endswith('.csv') and PurePosixPath(n).stem.isdigit()),
                     key=lambda n:int(PurePosixPath(n).stem))
        assert len(names)==1250
        # Bound format/completeness inspection to the first128 IDs. Others stay unparsed.
        inspected=names[args.skip_files:args.skip_files+128]
        assert len(inspected)==128
        for name in inspected:
            data=source.read(name)
            file_hashes[name]=hashlib.sha256(data).hexdigest()
            rows=csv.reader(io.StringIO(data.decode('utf-8-sig')),delimiter=';')
            header=[s.strip() for s in next(rows)]
            assert header[0]=='Timestamp [ms]' and header[4]=='CPU usage [%]',header
            days=defaultdict(dict);invalid=set();deltas=defaultdict(list);previous={}
            for row in rows:
                if not row or not row[0].strip():continue
                raw=float(row[0]);timestamp=raw/1000 if raw>=1e11 else raw
                unit='milliseconds' if raw>=1e11 else 'seconds despite header label'
                units[name]=unit
                day=int(timestamp//86400);slot=int(timestamp%86400//300)
                cpu=float(row[4])/100
                if not 0<=cpu<=1 or slot in days[day]: invalid.add(day)
                if day in previous:deltas[day].append(timestamp-previous[day])
                previous[day]=timestamp
                days[day][slot]=cpu
            valid={}
            for day,values in days.items():
                if day in invalid:rejected['invalid_cpu_or_duplicate_bin']+=1;continue
                if set(values)!=set(range(288)):rejected['incomplete_day']+=1;continue
                if any(abs(d-300)>1 for d in deltas[day]):rejected['irregular_interval']+=1;continue
                valid[day]=[values[i] for i in range(288)]
                timesteps[name,day]=(min(deltas[day]),max(deltas[day]))
            series[name]=valid
        counts=defaultdict(list)
        for name in inspected:
            for day in series[name]:counts[day].append(name)
        eligible=sorted(day for day,identifiers in counts.items() if len(identifiers)>=76)
        assert eligible,'No common complete day among first128 IDs; do not silently relax criteria'
        day=eligible[0]
        chosen=[name for name in inspected if day in series[name]][:76]
        date=datetime.fromtimestamp(day*86400,timezone.utc).strftime('%Y-%m-%d')
        provenance=dict(evaluated_utc=datetime.now(timezone.utc).isoformat(),archive_sha256=DIGEST,
            selected_day_utc=date,source_files=len(names),format_time_inspected_ids=[PurePosixPath(n).stem for n in inspected],
            evaluated_ids=[PurePosixPath(n).stem for n in chosen],unparsed_reserved_files=len(names)-args.skip_files-len(inspected),
            later_days='Eligibility parsed but no model outcomes evaluated on later days',timestamp_units={n:units[n] for n in chosen},
            alignment='Five-minute UTC bins;288 unique consecutive samples, step300s±1; no interpolation or filling',
            cpu_normalization='CPU usage percent divided by100; uniform VM capacity in forecast-only groups, not provider resource reconstruction',
            chosen_file_sha256={n:file_hashes[n] for n in chosen},rejected_vm_days=dict(rejected),
            model_code_sha256=hashlib.sha256((CODE/'scripts/research/validate_complete_sequences.py').read_bytes()).hexdigest())
        (ROOT/'provenance.json').write_text(json.dumps(provenance,indent=2)+'\n')
        with gzip.open(ROOT/'normalized_vm_cpu.csv.gz','wt',newline='') as stream:
            writer=csv.writer(stream);writer.writerow(['vm','source']+[f'cpu_{i*300}s' for i in range(288)])
            for name in chosen:writer.writerow([PurePosixPath(name).stem,name]+series[name][day])
        groups=[]
        for start in range(0,76,4):
            members=chosen[start:start+4]
            groups.append((members,[.25*sum(series[n][day][i] for n in members) for i in range(288)]))
        with gzip.open(ROOT/f'bitbrains_{date.replace("-","")}_inputs.csv.gz','wt',newline='') as stream:
            writer=csv.writer(stream);writer.writerow(['host','traces']+[f'cpu_{i*300}s' for i in range(288)])
            for host,(members,values) in enumerate(groups):writer.writerow([host,';'.join(members)]+values)
        result=evaluate(groups)
        result['scope']='First-use independent-provider forecast-only validation;19 fixed normalized host groups; no simulator/energy claim'
        (ROOT/'results.json').write_text(json.dumps(result,indent=2)+'\n')
        print('Selected',date,'76 VMs in19 hosts; unparsed reserved files:',len(names)-args.skip_files-len(inspected))
        for model,row in result['summary'].items():
            print(model,'MAE_pp=',round(row['mae_pp'],5),'RMSE_pp=',round(row['rmse_pp'],5),flush=True)


if __name__=='__main__':main()
