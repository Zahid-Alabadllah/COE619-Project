"""Audit newest input age on saved corrected-power forecasts; no model changes."""
from pathlib import Path
from collections import defaultdict
import gzip
import json
import re
import csv
import argparse

CODE=Path(__file__).resolve().parents[2]
ROOT=CODE/'results/predictor_investigation'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run',default='online_holdout_20110420/bounded_power_mean')
    parser.add_argument('--output',default='history_age_audit.json')
    parser.add_argument('--require-closed',action='store_true')
    args=parser.parse_args()
    source=ROOT/args.run/'simulation.log.gz'
    inputs={};groups=defaultdict(list);case=None;issued={};matched=set();excluded={};duplicates=0
    for line in gzip.open(source,'rt'):
        if line.startswith('CASE_START'):
            case=re.search(r'scenario=(\S+)',line)[1]
            inputs={}
        elif line.startswith('FORECAST_INPUT'):
            m=re.search(r'host=(\d+) target=([\d.]+) samples=([\d.]+):',line)
            age=float(m[2])-300-float(m[3])
            inputs[m[1],m[2]]=age
            issued_key=(case,m[1],m[2])
            duplicates+=issued_key in issued
            issued[issued_key]=age
        elif line.startswith('FORECAST_ERROR'):
            m=re.search(r'host=(\d+) target=([\d.]+) actual=([-+\deE.]+) forecast=([-+\deE.]+) persistence=([-+\deE.]+)',line)
            key=m[1],m[2]
            if key not in inputs:continue
            age=inputs[key]
            matched.add((case,m[1],m[2]))
            assert age>=-.001,'Input timestamp later than issue time'
            group='current' if age<=1 else 'one_interval_old' if 299<=age<=301 else 'other'
            groups[case,group].append((age,abs(float(m[4])-float(m[3])),abs(float(m[5])-float(m[3]))))
        elif line.startswith('FORECAST_EXCLUDED'):
            m=re.search(r'host=(\d+) target=([\d.]+) reason=(\w+)',line)
            key=case,m[1],m[2]
            assert key in issued and key not in matched and key not in excluded
            excluded[key]=m[3]
    report=dict(source=source.relative_to(CODE).as_posix(),scope='Matched corrected-power calls only; age groups are observations, not causal explanations',groups={})
    coverage=defaultdict(lambda:dict(issued=0,matched=0,unmatched=0))
    for key,age in issued.items():
        group='current' if age<=1 else 'one_interval_old' if 299<=age<=301 else 'other'
        row=coverage[key[0]+'/'+group]
        row['issued']+=1
        row['matched' if key in matched else 'unmatched']+=1
    report['coverage']=dict(coverage)
    report['duplicate_rounded_keys']=duplicates
    ends={r['scenario']:float(r['end_time_s']) for r in csv.DictReader(source.with_name('experiments.csv').open()) if r['policy']=='LR'}
    report['unmatched']=[dict(case=k[0],host=k[1],target_s=float(k[2]),latest_age_s=age)
                         for k,age in issued.items() if k not in matched]
    report['unmatched_boundary_counts']={name:sum(
        ('beyond_end' if float(k[2])>ends[k[0]]+1 else 'at_end' if abs(float(k[2])-ends[k[0]])<=1 else 'interior')==name
        for k in issued if k not in matched) for name in ('beyond_end','at_end','interior')}
    report['exclusions']={reason:sum(x==reason for x in excluded.values()) for reason in sorted(set(excluded.values()))}
    unaccounted=set(issued)-matched-set(excluded)
    report['unaccounted']=len(unaccounted)
    if args.require_closed:
        assert not duplicates and not unaccounted and not (matched & set(excluded))
        assert len(issued)==len(matched)+len(excluded)
    for (case,group),rows in groups.items():
        result=dict(n=len(rows),min_age_s=min(r[0] for r in rows),max_age_s=max(r[0] for r in rows),
            candidate_mae_pp=100*sum(r[1] for r in rows)/len(rows),persistence_mae_pp=100*sum(r[2] for r in rows)/len(rows))
        report['groups'][case+'/'+group]=result
        print(case,group,result)
    print('Issued/matched coverage:',dict(coverage),'rounded-key duplicates:',duplicates)
    (ROOT/args.output).write_text(json.dumps(report,indent=2)+'\n')


if __name__=='__main__':main()

