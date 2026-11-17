"""Verify logged Java blend weights against an independent causal event replay."""
from pathlib import Path
from collections import defaultdict, deque
import argparse
import gzip
import json
import math
import re


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    opener = gzip.open if args.log.suffix == '.gz' else open
    states, inputs, errors = {}, {}, defaultdict(list)
    max_weight_difference = max_prediction_difference = 0
    checks = 0
    case = policy = None
    with opener(args.log, 'rt', encoding='utf-8-sig') as stream:
        for line in stream:
            if line.startswith('CASE_START'):
                match = re.search(r'scenario=(\S+) policy=(\S+)', line)
                case, policy = match.groups()
                states, inputs = {}, {}
            elif line.startswith('FORECAST_INPUT'):
                match = re.search(r'host=(\d+) target=([\d.]+) samples=(.*)', line)
                host, target_text, values = match.groups()
                target = float(target_text)
                samples = [tuple(map(float, x.split(':'))) for x in values.split(';')]
                mt = sum(t for t, u in samples) / len(samples)
                mu = sum(u for t, u in samples) / len(samples)
                denominator = sum((t-mt)**2 for t, u in samples)
                slope = sum((t-mt)*(u-mu) for t, u in samples) / denominator if denominator else 0
                inputs[host, target_text] = dict(latest=samples[0][1], raw=mu+slope*(target-mt))
            elif line.startswith('FORECAST_BLEND'):
                match = re.search(r'host=(\d+) target=([\d.]+) weight=([\d.]+) online=(\w+)', line)
                host, target_text, weight_text, online = match.groups()
                assert online == 'true', 'Expected online candidate log'
                history, ready = states.setdefault(host, (deque(maxlen=20), []))
                issue = float(target_text) - 300
                eligible = [x for x in ready if x[0] < issue - 1e-6]
                history.extend((trend, change) for target, trend, change in eligible)
                ready[:] = [x for x in ready if x[0] >= issue - 1e-6]
                denominator = sum(d*d for d, y in history)
                expected = max(0, min(1, sum(d*y for d, y in history)/denominator)) if len(history) >= 10 and denominator > 1e-12 else .5
                weight = float(weight_text)
                difference = abs(expected-weight)
                max_weight_difference = max(max_weight_difference, difference)
                assert difference < 1e-5, (case, host, target_text, expected, weight)
                inputs[host, target_text]['weight'] = weight
                checks += 1
            elif line.startswith('FORECAST_ERROR'):
                match = re.search(r'host=(\d+) target=([\d.]+) actual=([-+\d.]+) forecast=([-+\d.]+) persistence=([-+\d.]+)', line)
                host, target_text, actual_text, predicted_text, latest_text = match.groups()
                actual, predicted = float(actual_text), float(predicted_text)
                row = inputs[host, target_text]
                latest, raw = row['latest'], row['raw']
                reconstructed = latest + row['weight']*(raw-latest)
                difference = abs(reconstructed-predicted)
                max_prediction_difference = max(max_prediction_difference, difference)
                assert difference < 1e-5, (case, host, target_text, reconstructed, predicted)
                states[host][1].append((float(target_text), raw-latest, actual-latest))
                errors[case].append(dict(actual=actual, persistence=latest-actual, ols=raw-actual,
                                         half=latest+.5*(raw-latest)-actual, online=predicted-actual))
    assert checks and errors, 'No online predictions found'
    report = dict(weight_checks=checks, max_weight_difference=max_weight_difference,
                  max_prediction_difference=max_prediction_difference, accuracy={})
    for case, rows in errors.items():
        report['accuracy'][case] = {}
        for model in ('persistence', 'ols', 'half', 'online'):
            values = [r[model] for r in rows]
            report['accuracy'][case][model] = dict(count=len(values),
                mae_pp=100*sum(map(abs, values))/len(values),
                rmse_pp=100*math.sqrt(sum(x*x for x in values)/len(values)))
            # Conditional 80%-threshold checks, not recall over every host moment.
            tp = fp = fn = tn = 0
            for row in rows:
                warning = row['actual'] + row[model] > .8
                overloaded = row['actual'] > .8
                tp += warning and overloaded
                fp += warning and not overloaded
                fn += not warning and overloaded
                tn += not warning and not overloaded
            report['accuracy'][case][model]['conditional_threshold'] = dict(
                threshold=.8, true_positive=tp, false_positive=fp, false_negative=fn, true_negative=tn,
                precision=tp/(tp+fp) if tp+fp else None,
                recall=tp/(tp+fn) if tp+fn else None)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2)+'\n')
    print('PASS:', checks, 'causal weight checks; maximum difference', max_weight_difference)
    for case, models in report['accuracy'].items():
        print(case, ' '.join(f'{m}={v["mae_pp"]:.3f}' for m, v in models.items()))


if __name__ == '__main__':
    main()
