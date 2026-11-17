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
    candidate_model = None
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
            elif line.startswith('FORECAST_MEAN_INPUT'):
                match = re.search(r'host=(\d+) target=([\d.]+) samples=(.*)', line)
                host, target_text, values = match.groups()
                samples = [tuple(map(float,x.split(':'))) for x in values.split(';')]
                assert 10 <= len(samples) <= 20
                assert all(t <= float(target_text)-300+.001 for t,u in samples), 'Future mean input'
                assert all(samples[i][0]-samples[i+1][0] >= 300-1e-6 for i in range(len(samples)-1)), 'Clumped mean inputs'
                inputs[host,target_text]['mean'] = sum(u for t,u in samples)/len(samples)
            elif line.startswith('FORECAST_BLEND'):
                match = re.search(r'host=(\d+) target=([\d.]+) weight=([\d.]+) online=(\w+)(?: model=(\w+))?', line)
                host, target_text, weight_text, online, model = match.groups()
                model = model or 'trend'
                if candidate_model is None: candidate_model = model
                assert candidate_model == model, 'Do not mix candidate models in one report'
                assert online == 'true', 'Expected online candidate log'
                history, ready = states.setdefault(host, (deque(maxlen=20), []))
                issue = float(target_text) - 300
                eligible = [x for x in ready if x[0] < issue - 1e-6]
                history.extend((trend, change, latest, actual, old_trend, old_pair, old_mean)
                               for target, trend, change, latest, actual, old_trend, old_pair, old_mean in eligible)
                ready[:] = [x for x in ready if x[0] >= issue - 1e-6]
                denominator = sum(h[0]*h[0] for h in history)
                expected = max(0, min(1, sum(h[0]*h[1] for h in history)/denominator)) if len(history) >= 10 and denominator > 1e-12 else .5
                weight = float(weight_text)
                difference = abs(expected-weight)
                max_weight_difference = max(max_weight_difference, difference)
                assert difference < 1e-5, (case, host, target_text, expected, weight)
                inputs[host, target_text]['weight'] = weight
                latest = inputs[host, target_text]['latest']
                pair = latest
                if len(history) >= 10:
                    mx = sum(h[2] for h in history)/len(history)
                    my = sum(h[3] for h in history)/len(history)
                    variance = sum((h[2]-mx)**2 for h in history)
                    if variance > 1e-12:
                        slope = max(0, min(1, sum((h[2]-mx)*(h[3]-my) for h in history)/variance))
                        pair = max(0, min(1, my+slope*(latest-mx)))
                inputs[host, target_text]['pair'] = pair
                inputs[host, target_text]['model'] = model
                trend = latest + weight*(inputs[host, target_text]['raw']-latest)
                choose_pair = len(history) >= 10 and sum(abs(h[5]-h[3]) for h in history) < sum(abs(h[4]-h[3]) for h in history)
                inputs[host, target_text]['selected'] = pair if choose_pair else trend
                inputs[host, target_text]['trend'] = trend
                mean = inputs[host,target_text].get('mean',float('nan'))
                mean_history = [h for h in history if math.isfinite(h[6])]
                choose_mean = len(mean_history) >= 10 and sum(abs(h[6]-h[3]) for h in mean_history) < sum(abs(h[4]-h[3]) for h in mean_history)
                inputs[host,target_text]['mean_selected'] = mean if choose_mean else trend
                checks += 1
            elif line.startswith('FORECAST_ERROR'):
                match = re.search(r'host=(\d+) target=([\d.]+) actual=([-+\d.]+) forecast=([-+\d.]+) persistence=([-+\d.]+)', line)
                host, target_text, actual_text, predicted_text, latest_text = match.groups()
                actual, predicted = float(actual_text), float(predicted_text)
                row = inputs[host, target_text]
                latest, raw = row['latest'], row['raw']
                reconstructed = row['mean_selected'] if row['model'] == 'mean_selected' else row['selected'] if row['model'] == 'selected' else row['pair'] if row['model'] == 'recent_pairs' else row['trend']
                difference = abs(reconstructed-predicted)
                max_prediction_difference = max(max_prediction_difference, difference)
                assert difference < 1e-5, (case, host, target_text, reconstructed, predicted)
                states[host][1].append((float(target_text), raw-latest, actual-latest, latest, actual,
                                       row['trend'], row['pair'], row.get('mean',float('nan'))))
                errors[case].append(dict(actual=actual, persistence=latest-actual, ols=raw-actual,
                                         half=latest+.5*(raw-latest)-actual,
                                         adaptive_replay=latest+row['weight']*(raw-latest)-actual,
                                         recent_pairs=row['pair']-actual, selected_replay=row['selected']-actual,
                                         mean_replay=row.get('mean',float('nan'))-actual,
                                         mean_selected_replay=row['mean_selected']-actual, online=predicted-actual))
    assert checks and errors, 'No online predictions found'
    report = dict(candidate_model=candidate_model, weight_checks=checks, max_weight_difference=max_weight_difference,
                  max_prediction_difference=max_prediction_difference, accuracy={})
    for case, rows in errors.items():
        report['accuracy'][case] = {}
        models = ['persistence', 'ols', 'half', 'adaptive_replay', 'recent_pairs', 'selected_replay', 'online']
        if candidate_model == 'mean_selected': models += ['mean_replay','mean_selected_replay']
        for model in models:
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
