"""Exploratory causal absolute-error blend; no new data or Java changes."""
from collections import deque
from datetime import datetime, timezone
import csv
import gzip
import hashlib
import json
import math
import sys
sys.dont_write_bytecode = True
from validate_complete_sequences import CODE, forecasts


def absolute_weight(history):
    """Minimize sum |weight*direction - observed_change| on [0,1]."""
    if len(history) < 10:
        return 0.0
    pairs = sorted((change / direction, abs(direction))
                   for direction, change in history if abs(direction) > 1e-12)
    if not pairs:
        return 0.0
    total = sum(weight for ratio, weight in pairs)
    cumulative = 0.0
    for ratio, weight in pairs:
        cumulative += weight
        if cumulative >= total / 2:
            return max(0.0, min(1.0, ratio))
    raise AssertionError('Missing weighted median')


def main():
    output = CODE / 'results/predictor_investigation/absolute_blend_development'
    output.mkdir(parents=True, exist_ok=True)
    assert not (output / 'plan.json').exists(), 'Do not overwrite earlier evidence'
    files = [path for folder in ('bitbrains_holdout', 'bitbrains_reserved_vms',
                                'complete_sequences_mean_confirm')
             for path in sorted((CODE/'results/predictor_investigation'/folder).glob('*_inputs.csv.gz'))]
    assert files
    plan = dict(recorded_utc=datetime.now(timezone.utc).isoformat(),
                scope='Exploratory reuse of inspected data only; no fresh validation or energy claim',
                primary='MAE', secondary='RMSE and per-host MAE',
                model='Blend bounded trend/mean selector with persistence; minimize absolute error on last20 strictly earlier resolved forecasts; min10, initial weight0, clip weight0..1; no parameter search',
                source_sha256=hashlib.sha256(__file__.encode()).hexdigest(),
                inputs={p.relative_to(CODE).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in files})
    plan['source_sha256'] = hashlib.sha256(open(__file__, 'rb').read()).hexdigest()
    (output/'plan.json').write_text(json.dumps(plan, indent=2)+'\n')
    report = {}
    for path in files:
        with gzip.open(path, 'rt') as stream:
            rows = list(csv.reader(stream))[1:]
        errors = {name: [] for name in ('persistence', 'ols_clipped', 'adaptive_clipped',
                                       'mean_switching_clipped', 'three_way_mae', 'absolute_blend')}
        host_results = []
        for row in rows:
            series = list(map(float, row[2:]))
            past, pending = deque(maxlen=20), deque()
            own = {name: [] for name in errors}
            for target, actual, models in forecasts(series):
                issue = target - 1
                while pending and pending[0][0] < issue:
                    old_target, direction, latest = pending.popleft()
                    past.append((direction, series[old_target]-latest))
                latest = series[issue]
                direction = models['mean_switching_clipped'] - latest
                weight = absolute_weight(past)
                predicted = latest + weight * direction
                assert 0 <= predicted <= 1
                for name in errors:
                    error = (predicted if name == 'absolute_blend' else models[name])-actual
                    errors[name].append(error)
                    own[name].append(error)
                pending.append((target, direction, latest))
            host_results.append({name: 100*sum(map(abs, values))/len(values) for name, values in own.items()})
        scores = {name: dict(n=len(values), mae_pp=100*sum(map(abs, values))/len(values),
                            rmse_pp=100*math.sqrt(sum(x*x for x in values)/len(values)))
                  for name, values in errors.items()}
        report[path.name] = dict(summary=scores, hosts=host_results)
        print(path.name, ' '.join(f'{name}: MAE={r["mae_pp"]:.4f}, RMSE={r["rmse_pp"]:.4f}' for name,r in scores.items()), flush=True)
    (output/'results.json').write_text(json.dumps(report, indent=2)+'\n')


if __name__ == '__main__':
    main()
