"""Test per-VM forecasts versus direct host forecasts, without changing the simulator."""
from pathlib import Path, PurePosixPath
from datetime import datetime, timezone
import argparse
import hashlib
import json
import math
import sys
import zipfile
sys.dont_write_bytecode = True
from validate_complete_sequences import CODE, DIGEST, forecasts


def evaluate(pool, groups):
    predicted = {}
    for name, series in pool.items():
        values = [None]*288
        for target, actual, models in forecasts(series):
            values[target] = models['local_level']
        predicted[name] = values
    models = ('persistence','ols','adaptive','host_pairs','vm_pairs')
    totals = {m: dict(n=0, absolute=0., square=0., early_tp=0,early_fp=0,early_fn=0) for m in models}
    for members in groups:
        actuals = [.25*sum(pool[n][i] for n in members) for i in range(288)]
        for target, actual, host in forecasts(actuals):
            estimates = dict(persistence=host['persistence'],ols=host['ols'],adaptive=host['adaptive'],
                             host_pairs=host['local_level'],vm_pairs=.25*sum(predicted[n][target] for n in members))
            for model, estimate in estimates.items():
                row=totals[model];error=estimate-actual
                row['n']+=1;row['absolute']+=abs(error);row['square']+=error*error
                if actuals[target-1] <= .8:
                    warning, overload=estimate>.8,actual>.8
                    row['early_tp']+=warning and overload
                    row['early_fp']+=warning and not overload
                    row['early_fn']+=not warning and overload
    for row in totals.values():
        row['mae_pp']=100*row.pop('absolute')/row['n']
        row['rmse_pp']=100*math.sqrt(row.pop('square')/row['n'])
        row['early_precision']=row['early_tp']/(row['early_tp']+row['early_fp']) if row['early_tp']+row['early_fp'] else None
        row['early_recall']=row['early_tp']/(row['early_tp']+row['early_fn']) if row['early_tp']+row['early_fn'] else None
    return totals


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--days',nargs='+',default=['20110411','20110412'])
    args=parser.parse_args()
    root=CODE/'results/predictor_investigation/vm_aggregation'
    root.mkdir(parents=True,exist_ok=True)
    assert not (root/'plan.json').exists(),'Do not overwrite prior evidence'
    (root/'plan.json').write_text(json.dumps(dict(recorded_utc=datetime.now(timezone.utc).isoformat(),
        days=args.days,fresh_day='20110412',reused_day='20110411',
        model='Same recent-pair regression per VM, memory20/min10, slope/output0..1, persistence cold start, strictly earlier resolved targets',
        comparison='Fixed groups4 plus separate top16 whole-day-load stress group; no migration or energy simulation',
        archive_sha256=DIGEST),indent=2)+'\n')
    archive=CODE/'target/planetlab-source.zip'
    assert hashlib.sha256(archive.read_bytes()).hexdigest()==DIGEST
    report={}
    with zipfile.ZipFile(archive) as source:
        for day in args.days:
            pool={}
            for member in source.infolist():
                parts=PurePosixPath(member.filename).parts
                if not member.is_dir() and len(parts)==3 and parts[1]==day:
                    values=[float(x)/100 for x in source.read(member).decode().split()]
                    assert len(values)==288 and all(0<=x<=1 for x in values)
                    pool[parts[2]]=values
            assert pool
            names=sorted(pool)
            heavy=sorted(pool,key=lambda n:(-sum(pool[n]),n))[:16]
            report[day]={}
            for label,chosen in [('all',names),('stress',heavy)]:
                groups=[chosen[i:i+4] for i in range(0,len(chosen),4)]
                used={n:pool[n] for members in groups for n in members}
                result=evaluate(used,groups)
                report[day][label]=dict(trace_count=len(used),hosts=len(groups),metrics=result)
                print(day,label,' '.join(f'{m}={r["mae_pp"]:.3f}' for m,r in result.items()),flush=True)
            (root/'accuracy.json').write_text(json.dumps(report,indent=2)+'\n')


if __name__=='__main__':
    main()
