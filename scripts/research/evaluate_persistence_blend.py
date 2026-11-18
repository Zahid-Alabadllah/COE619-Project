"""Exploratory squared-error blend with persistence, on already inspected inputs."""
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


def main():
    root=CODE/'results/predictor_investigation/persistence_blend'
    root.mkdir(parents=True,exist_ok=True)
    assert not (root/'plan.json').exists(),'Do not overwrite earlier evidence'
    files=[p for folder in ('bitbrains_holdout','bitbrains_reserved_vms','complete_sequences_mean_confirm')
           for p in sorted((CODE/'results/predictor_investigation'/folder).glob('*_inputs.csv.gz'))]
    (root/'plan.json').write_text(json.dumps(dict(recorded_utc=datetime.now(timezone.utc).isoformat(),
        scope='Already inspected inputs; exploratory, no fresh-validation or energy claim',
        objective='Primary squared error/RMSE, report MAE too; no changes to Java',
        model='Blend persistence with bounded adaptive trend; rolling20 earlier errors/min10; initial persistence; least-squares blend weight clipped0..1; final forecast clipped0..1',
        inputs=[p.relative_to(CODE).as_posix() for p in files]),indent=2)+'\n')
    report={}
    for path in files:
        with gzip.open(path,'rt') as stream: rows=list(csv.reader(stream))[1:]
        errors={m:[] for m in ('persistence','adaptive_clipped','mean_switching_clipped','blend')}
        for row in rows:
            series=list(map(float,row[2:]));past=deque(maxlen=20);pending=deque()
            for target,actual,models in forecasts(series):
                issue=target-1
                while pending and pending[0][0]<issue:
                    old_target,base,latest=pending.popleft()
                    past.append((base-latest,series[old_target]-latest))
                denominator=sum(d*d for d,y in past)
                weight=max(0,min(1,sum(d*y for d,y in past)/denominator)) if len(past)>=10 and denominator>1e-12 else 0
                base=models['adaptive_clipped'];latest=series[issue]
                blend=max(0,min(1,latest+weight*(base-latest)))
                for name in errors:errors[name].append((blend if name=='blend' else models[name])-actual)
                pending.append((target,base,latest))
        scores={m:dict(n=len(e),mae_pp=100*sum(map(abs,e))/len(e),rmse_pp=100*math.sqrt(sum(x*x for x in e)/len(e))) for m,e in errors.items()}
        report[path.name]=scores
        print(path.name, ' '.join(f'{m}: MAE={r["mae_pp"]:.4f}, RMSE={r["rmse_pp"]:.4f}' for m,r in scores.items()))
    (root/'results.json').write_text(json.dumps(report,indent=2)+'\n')


if __name__=='__main__':main()
