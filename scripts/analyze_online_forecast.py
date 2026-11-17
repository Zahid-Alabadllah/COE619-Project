"""Causal replay of an adaptive trend blend. Research only; no policy changes."""
from pathlib import Path
from collections import defaultdict, deque
import gzip
import json
import math
import re

ROOT = Path(__file__).resolve().parents[1] / 'results/predictor_investigation'


def load(name):
    inputs, rows, case = {}, [], None
    with gzip.open(ROOT / name, 'rt') as stream:
        for line in stream:
            if line.startswith('CASE_START'):
                case = re.search(r'scenario=(\S+)', line)[1]
            elif line.startswith('FORECAST_INPUT'):
                m = re.search(r'host=(\d+) target=([\d.]+) samples=(.*)', line)
                inputs[case, m[1], m[2]] = [tuple(map(float, x.split(':'))) for x in m[3].split(';')]
            elif line.startswith('FORECAST_ERROR'):
                m = re.search(r'host=(\d+) target=([\d.]+) actual=([-+\d.]+)', line)
                key = case, m[1], m[2]
                if key not in inputs:
                    continue
                samples = inputs[key]
                target = float(m[2])
                mt = sum(x[0] for x in samples) / len(samples)
                mu = sum(x[1] for x in samples) / len(samples)
                xx = sum((t - mt) ** 2 for t, u in samples)
                slope = sum((t - mt) * (u - mu) for t, u in samples) / xx if xx else 0
                rows.append(dict(case=case, host=m[1], target=target,
                                 issue=target - 300, actual=float(m[3]),
                                 latest=samples[0][1], ols=mu + slope * (target - mt)))
    assert rows, name
    return rows


def replay(rows, memory):
    """Update weight only from targets strictly earlier than forecast issue time.

    Each case and host starts fresh. Even simultaneously issued forecasts cannot
    use one another's outcomes. Inputs remain the saved policy's conditional
    observations, so this is not an end-to-end policy or energy evaluation.
    """
    groups = defaultdict(list)
    for row in rows:
        groups[row['case'], row['host']].append(row)
    output = []
    for group in groups.values():
        history = deque(maxlen=memory)
        pending = deque()
        for row in sorted(group, key=lambda x: x['issue']):
            while pending and pending[0]['target'] < row['issue'] - 1e-6:
                old = pending.popleft()
                assert old['target'] < row['issue']
                history.append((old['ols'] - old['latest'], old['actual'] - old['latest']))
            denominator = sum(d * d for d, y in history)
            weight = max(0, min(1, sum(d * y for d, y in history) / denominator)) if len(history) >= 10 and denominator > 1e-12 else .5
            output.append({**row, 'adaptive': row['latest'] + weight * (row['ols'] - row['latest']), 'weight': weight})
            pending.append(row)
    return output


def score(rows, model):
    errors = []
    for row in rows:
        estimate = (row['latest'] + .5 * (row['ols'] - row['latest'])) if model == 'half' else row[model]
        errors.append(estimate - row['actual'])
    return dict(count=len(errors), mae_pp=100 * sum(map(abs, errors)) / len(errors),
                rmse_pp=100 * math.sqrt(sum(x*x for x in errors) / len(errors)))


def main():
    original = load('damped-normal-repaired.log.gz')
    times = sorted(row['target'] for row in original if row['case'] == 'real_a')
    cutoff = times[int(.7 * len(times))]
    # Choose memory on original-day early real_a only. Previously inspected
    # validation days are now reuse checks, not fresh confirmatory evidence.
    candidates = {}
    for memory in (20, 50, 100, None):
        rows = replay(original, memory)
        train = [r for r in rows if r['case'] == 'real_a' and r['target'] < cutoff]
        candidates[str(memory)] = score(train, 'adaptive')
    selected = min(candidates, key=lambda key: candidates[key]['mae_pp'])
    memory = None if selected == 'None' else int(selected)
    result = dict(selected_memory=memory, training_cutoff_s=cutoff, training_candidates=candidates,
                  limitations='Previously inspected data; conditional replay only; no energy claim.', validation={})
    for label, raw in [('original_day', original), ('reused_other_day', load('holdout-20110306.log.gz'))]:
        rows = replay(raw, memory)
        result['validation'][label] = {}
        for case in ('diurnal', 'real_a', 'real_b', 'real_c', 'heavy'):
            subset = [r for r in rows if r['case'] == case and (label != 'original_day' or case != 'real_a' or r['target'] >= cutoff)]
            if subset:
                result['validation'][label][case] = {m: score(subset, m) for m in ('latest', 'ols', 'half', 'adaptive')}
    (ROOT / 'online_forecast_accuracy.json').write_text(json.dumps(result, indent=2) + '\n')
    print('Selected memory:', memory)
    for day, cases in result['validation'].items():
        for case, models in cases.items():
            print(day, case, ' '.join(f'{m}={s["mae_pp"]:.3f}' for m, s in models.items()))


if __name__ == '__main__':
    main()
