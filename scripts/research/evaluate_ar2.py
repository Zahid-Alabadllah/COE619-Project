"""Exploratory two-reading regression with a persistence prior; Java unchanged."""
from pathlib import Path
from collections import deque
from datetime import datetime, timezone
import argparse
import csv
import gzip
import json
import math
import sys
sys.dont_write_bytecode=True
from validate_complete_sequences import CODE, forecasts


def estimate(pairs,latest,previous):
    if len(pairs)<20:return latest
    mx=sum(x for x,z,y in pairs)/len(pairs)
    mz=sum(z for x,z,y in pairs)/len(pairs)
    md=sum(y-x for x,z,y in pairs)/len(pairs)
    xx=sum((x-mx)**2 for x,z,y in pairs)+.0001
    zz=sum((z-mz)**2 for x,z,y in pairs)+.0001
    xz=sum((x-mx)*(z-mz) for x,z,y in pairs)
    xd=sum((x-mx)*(y-x-md) for x,z,y in pairs)
    zd=sum((z-mz)*(y-x-md) for x,z,y in pairs)
    determinant=xx*zz-xz*xz
    assert determinant>0
    b0=(xd*zz-zd*xz)/determinant
    b1=(zd*xx-xd*xz)/determinant
    return max(0,min(1,latest+md+b0*(latest-mx)+b1*(previous-mz)))


def evaluate(groups):
    errors={m:[] for m in ('persistence','adaptive_clipped','mean_switching_clipped','ar2')}
    for series in groups:
        pairs=deque(maxlen=20);pending=deque()
        for target,actual,models in forecasts(series):
            issue=target-1
            while pending and pending[0]<issue:
                old=pending.popleft();pairs.append((series[old-1],series[old-2],series[old]))
            prediction=estimate(pairs,series[issue],series[issue-1])
            for name in errors:errors[name].append((prediction if name=='ar2' else models[name])-actual)
            pending.append(target)
    return {m:dict(n=len(e),mae_pp=100*sum(map(abs,e))/len(e),rmse_pp=100*math.sqrt(sum(x*x for x in e)/len(e))) for m,e in errors.items()}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs',type=Path,nargs='+')
    parser.add_argument('--output',type=Path,default=CODE/'results/predictor_investigation/ar2_development')
    args=parser.parse_args()
    files=args.inputs or [p for folder in ('bitbrains_holdout','bitbrains_reserved_vms','complete_sequences_mean_confirm')
        for p in sorted((CODE/'results/predictor_investigation'/folder).glob('*_inputs.csv.gz'))]
    args.output.mkdir(parents=True,exist_ok=True)
    assert not (args.output/'plan.json').exists(),'Do not overwrite study'
    (args.output/'plan.json').write_text(json.dumps(dict(recorded_utc=datetime.now(timezone.utc).isoformat(),
        scope='All default development inputs already inspected; no fresh-validation or energy claim',
        objective='Primary RMSE/tail error; report MAE without claiming dominance',
        model='Two most recent CPU readings predict next; rolling20 strictly earlier triples; min20 persistence fallback; ridge0.0001 on centered residual-to-persistence coefficients; output0..1',
        inputs=[str(p) for p in files]),indent=2)+'\n')
    report={}
    for path in files:
        with gzip.open(path,'rt') as stream:rows=list(csv.reader(stream))[1:]
        scores=evaluate([list(map(float,row[2:])) for row in rows])
        report[path.name]=scores
        print(path.name,' '.join(f'{m}: MAE={r["mae_pp"]:.4f}, RMSE={r["rmse_pp"]:.4f}' for m,r in scores.items()),flush=True)
    (args.output/'results.json').write_text(json.dumps(report,indent=2)+'\n')


if __name__=='__main__':main()
