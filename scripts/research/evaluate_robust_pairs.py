"""Robust past-to-next CPU regression on reused development inputs only."""
from collections import deque
from datetime import datetime, timezone
import csv
import gzip
import hashlib
import json
import math
from pathlib import Path
import statistics
import sys
sys.dont_write_bytecode = True
from validate_complete_sequences import CODE, forecasts

REFERENCE = 'https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.theilslopes.html'


def robust_pair(pairs, latest):
    if len(pairs) < 10:
        return latest
    slopes = [(y2-y1)/(x2-x1) for i,(x1,y1) in enumerate(pairs)
              for x2,y2 in list(pairs)[i+1:] if abs(x2-x1)>1e-12]
    # Projecting the slope and CPU forecast are our conservative design choices.
    slope = max(0.0, min(1.0, statistics.median(slopes))) if slopes else 0.0
    intercept = statistics.median(y-slope*x for x,y in pairs)
    return max(0.0, min(1.0, intercept+slope*latest))


def predictions(series):
    pairs, errors, pending = deque(maxlen=20), deque(maxlen=20), deque()
    for target, actual, models in forecasts(series):
        issue = target-1
        while pending and pending[0][0] < issue:
            old_target, old_latest, old_robust = pending.popleft()
            pairs.append((old_latest, series[old_target]))
            errors.append((abs(old_latest-series[old_target]), abs(old_robust-series[old_target])))
        latest = series[issue]
        robust = robust_pair(pairs, latest)
        choose = len(errors)>=10 and sum(e[1] for e in errors)<sum(e[0] for e in errors)
        models['robust_pairs'] = robust
        models['robust_or_persistence'] = robust if choose else latest
        yield target, actual, models
        pending.append((target, latest, robust))


def main():
    root = CODE/'results/predictor_investigation/robust_pairs_development'
    root.mkdir(parents=True, exist_ok=True)
    assert not (root/'plan.json').exists(), 'Do not overwrite evidence'
    files = [p for folder in ('bitbrains_holdout','bitbrains_reserved_vms','complete_sequences_mean_confirm')
             for p in sorted((CODE/'results/predictor_investigation'/folder).glob('*_inputs.csv.gz'))]
    raw = Path(__file__).read_bytes()
    plan = dict(recorded_utc=datetime.now(timezone.utc).isoformat(),
                scope='Exploratory reuse only; no new reserved VM outcomes; no energy claim',
                primary='MAE', secondary='RMSE, per-host MAE and next-step 80% warnings',
                published_method='Theil-Sen median pairwise slopes; joint median intercept',reference=REFERENCE,
                project_choices='Regress next CPU on previous CPU using20 strictly earlier pairs, min10; slope bounded0..1, forecast bounded0..1; no distinct x gives median next CPU; initial persistence. Secondary conservative selector uses20 strictly earlier MAEs, min10, persistence on warmup/tie. No parameter search.',
                source_sha256=hashlib.sha256(raw).hexdigest(),
                inputs={p.relative_to(CODE).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in files})
    (root/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
    (root/'frozen_forecast_source.py.txt').write_bytes(raw)
    names=('persistence','ols_clipped','adaptive_clipped','mean_switching_clipped','local_level',
           'robust_pairs','robust_or_persistence')
    report={}
    for path in files:
        with gzip.open(path,'rt') as f: rows=list(csv.reader(f))[1:]
        errors={m:[] for m in names}
        warnings={m:dict(tp=0,fp=0,fn=0,tn=0) for m in names}
        hosts=[]
        for row in rows:
            series=list(map(float,row[2:]));own={m:[] for m in names}
            for target,actual,models in predictions(series):
                for name in names:
                    estimate=models[name]
                    error=estimate-actual
                    errors[name].append(error);own[name].append(error)
                    key='tp' if estimate>.8 and actual>.8 else 'fp' if estimate>.8 else 'fn' if actual>.8 else 'tn'
                    warnings[name][key]+=1
            hosts.append({m:100*sum(map(abs,e))/len(e) for m,e in own.items()})
        summary={m:dict(n=len(e),mae_pp=100*sum(map(abs,e))/len(e),
                        rmse_pp=100*math.sqrt(sum(x*x for x in e)/len(e)),threshold80=warnings[m]) for m,e in errors.items()}
        report[path.name]=dict(summary=summary,hosts=hosts)
        print(path.name,' '.join(f'{m}: MAE={r["mae_pp"]:.4f}, RMSE={r["rmse_pp"]:.4f}' for m,r in summary.items()),flush=True)
    (root/'results.json').write_text(json.dumps(report,indent=2)+'\n')


if __name__=='__main__':main()
