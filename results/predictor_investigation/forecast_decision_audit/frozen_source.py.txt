"""Offline reused-data audit of trend recovery and brief-overload deferral opportunities."""
from collections import deque
from datetime import datetime, timezone
import csv
import gzip
import hashlib
import json
import math
from pathlib import Path
import sys
sys.dont_write_bytecode=True
from evaluate_robust_pairs import CODE, predictions


def combined_predictions(series):
    history,pending=deque(maxlen=20),deque()
    for target,actual,models in predictions(series):
        issue=target-1
        while pending and pending[0][0]<issue:
            old,robust,trend=pending.popleft()
            history.append((abs(robust-series[old]),abs(trend-series[old])))
        choose=len(history)>=10 and sum(x[1] for x in history)<sum(x[0] for x in history)
        models['robust_trend_switch']=models['ols_clipped'] if choose else models['robust_or_persistence']
        yield target,actual,models
        pending.append((target,models['robust_or_persistence'],models['ols_clipped']))


def evaluate(groups):
    names=('persistence','ols_clipped','mean_switching_clipped','robust_or_persistence','robust_trend_switch')
    scores={name:dict(n=0,absolute=0.,square=0.,eligible80to95=0,deferred=0,
                     next_below80=0,next_still_above80=0,next_above95=0) for name in names}
    delay=dict(deferred=0,next_below80=0,next_still_above80=0,next_above95=0)
    for series in groups:
        for target,actual,models in combined_predictions(series):
            latest=series[target-1]
            eligible=.8<latest<.95
            first_crossing=eligible and series[target-2]<=.8
            if first_crossing:
                delay['deferred']+=1;delay['next_below80']+=actual<=.8
                delay['next_still_above80']+=actual>.8;delay['next_above95']+=actual>.95
            for name in names:
                row=scores[name];error=models[name]-actual
                row['n']+=1;row['absolute']+=abs(error);row['square']+=error*error
                row['eligible80to95']+=eligible
                if eligible and models[name]<=.8:
                    row['deferred']+=1;row['next_below80']+=actual<=.8
                    row['next_still_above80']+=actual>.8;row['next_above95']+=actual>.95
    for row in scores.values():
        row['mae_pp']=100*row.pop('absolute')/row['n']
        row['rmse_pp']=100*math.sqrt(row.pop('square')/row['n'])
    return dict(models=scores,first_crossing_one_step_delay=delay)


def main():
    root=CODE/'results/predictor_investigation/forecast_decision_audit'
    root.mkdir(parents=True,exist_ok=True)
    assert not (root/'plan.json').exists(),'Do not overwrite evidence'
    paths=[p for folder in ('bitbrains_holdout','bitbrains_reserved_vms','bitbrains_robust_reserved',
                           'bitbrains_robust_confirm','complete_sequences_mean_confirm')
           for p in sorted((CODE/'results/predictor_investigation'/folder).glob('*_inputs.csv.gz'))]
    plan=dict(recorded_utc=datetime.now(timezone.utc).isoformat(),
              scope='Previously inspected fixed-placement data only; exploratory, no migration or energy simulation',
              primary='Assess feasible next-step brief-overload deferral; report continued overloads and >95% outcomes, not only successful deferrals',
              secondary='MAE/RMSE of robust-versus-OLS selector for synthetic trend recovery',
              choices='Current CPU above80 and below95 may be deferred if forecast<=80.95 is an illustrative project safety ceiling, not an SLA guarantee. Delay control defers first80 crossing below95 for one step. No parameter search.',
              selector='Choose bounded OLS versus robust/persistence by20 strictly earlier absolute errors, min10; warmup/tie robust/persistence',
              source_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              inputs={str(p.relative_to(CODE)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths})
    (root/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
    results={}
    for path in paths:
        with gzip.open(path,'rt') as f:rows=list(csv.reader(f))[1:]
        key=path.parent.name+'/'+path.name
        assert key not in results
        results[key]=evaluate([list(map(float,row[2:])) for row in rows])
    manifest=(CODE/'src/main/resources/workloads/diurnal.txt').read_text().splitlines()
    traces=[[float(x)/100 for x in (CODE/p).read_text().split()] for p in manifest if p.strip()]
    results['synthetic_diurnal']=evaluate([[.25*sum(t[k] for t in traces[i:i+4]) for k in range(288)] for i in range(0,len(traces),4)])
    for name,result in results.items():
        print(name,flush=True)
        for model,row in result['models'].items():
            print(model,'MAE',round(row['mae_pp'],4),'RMSE',round(row['rmse_pp'],4),
                  'deferred',row['deferred'],'still80',row['next_still_above80'],'above95',row['next_above95'],flush=True)
        print('delay control',result['first_crossing_one_step_delay'],flush=True)
    (root/'results.json').write_text(json.dumps(results,indent=2)+'\n')


if __name__=='__main__':main()
