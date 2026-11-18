"""Exploratory causal error margins; no simulator or migration-rule changes."""
from pathlib import Path
from collections import deque
from datetime import datetime, timezone
import csv
import gzip
import json
import math
import sys
sys.dont_write_bytecode=True
from validate_complete_sequences import CODE, forecasts


def quantile(values, probability):
    ordered=sorted(values)
    index=(len(ordered)-1)*probability
    lower=math.floor(index)
    upper=math.ceil(index)
    return ordered[lower]+(index-lower)*(ordered[upper]-ordered[lower])


def evaluate(groups):
    names=('point','margin80','margin90')
    stats={name:dict(n=0,covered=0,early_tp=0,early_fp=0,early_fn=0,early_tn=0,width_sum=0.) for name in names}
    for series in groups:
        errors=deque(maxlen=20)
        pending=deque()
        for target,actual,models in forecasts(series):
            issue=target-1
            while pending and pending[0][0]<issue:
                old_target,old_estimate=pending.popleft()
                errors.append(series[old_target]-old_estimate)
            point=models['mean_switching']
            for name,probability in [('point',None),('margin80',.8),('margin90',.9)]:
                margin=max(0,quantile(errors,probability)) if probability is not None and len(errors)>=10 else 0
                upper=max(0,min(1,point+margin))
                row=stats[name]
                row['n']+=1
                row['covered']+=actual<=upper
                row['width_sum']+=max(0,upper-point)
                if series[issue]<=.8:
                    warn,overload=upper>.8,actual>.8
                    row['early_tp' if warn and overload else 'early_fp' if warn else 'early_fn' if overload else 'early_tn']+=1
            pending.append((target,point))
    for row in stats.values():
        row['coverage']=row['covered']/row['n']
        row['mean_margin_pp']=100*row.pop('width_sum')/row['n']
        row['early_precision']=row['early_tp']/(row['early_tp']+row['early_fp']) if row['early_tp']+row['early_fp'] else None
        row['early_recall']=row['early_tp']/(row['early_tp']+row['early_fn']) if row['early_tp']+row['early_fn'] else None
    return stats


def main():
    root=CODE/'results/predictor_investigation/forecast_margins'
    root.mkdir(parents=True,exist_ok=True)
    assert not (root/'plan.json').exists(),'Do not overwrite prior evidence'
    sources=[CODE/'results/predictor_investigation'/folder/file for folder,file in [
        ('complete_sequences_reused_stress','20110409_stress_inputs.csv.gz'),
        ('complete_sequences_stress_confirm','20110411_stress_inputs.csv.gz'),
        ('complete_sequences_mean_confirm','20110420_stress_inputs.csv.gz'),
        ('complete_sequences_mean_confirm','20110420_inputs.csv.gz')]]
    plan=dict(recorded_utc=datetime.now(timezone.utc).isoformat(),scope='Previously inspected fixed-placement inputs; exploratory only; no energy or novelty claim',
        model='Frozen trend/mean point forecast; nonnegative empirical .8 and .9 residual-quantile margins from last20 strictly earlier resolved errors, min10',
        caution='Nominal quantile levels are not coverage guarantees or formal confidence intervals; report observed coverage and early false warnings',
        inputs=[p.relative_to(CODE).as_posix() for p in sources])
    (root/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
    report={}
    for source in sources:
        with gzip.open(source,'rt') as stream:
            records=list(csv.reader(stream))[1:]
        result=evaluate([list(map(float,row[2:])) for row in records])
        report[source.name.removesuffix('_inputs.csv.gz')]=result
        print(source.name)
        for name,row in result.items():
            print(name,'recall=',row['early_recall'],'precision=',row['early_precision'],
                  'false=',row['early_fp'],'coverage=',round(row['coverage'],3),flush=True)
    (root/'results.json').write_text(json.dumps(report,indent=2)+'\n')


if __name__=='__main__':
    main()
