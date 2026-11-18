"""Evaluate every eligible next reading in fixed-placement host CPU sequences.

This isolates forecasting from migration decisions. It is not an energy simulation.
All model settings are fixed before opening the validation data.
"""
from pathlib import Path, PurePosixPath
from collections import deque
from datetime import datetime, timezone
import argparse
import csv
import gzip
import hashlib
import json
import math
import re
import statistics
import zipfile
from urllib.request import urlretrieve

CODE = Path(__file__).resolve().parents[2]
COMMIT = '344ffdbf7e528bfa6f276014e889e68a0cb640c2'
DIGEST = 'ef817107a2783a28283bcb9dcbe28c38977a14a7345e6171a4b8d3f00315a88f'
MODELS = ('persistence', 'mean20', 'median20', 'ema20', 'ols', 'ols_clipped', 'half', 'adaptive', 'adaptive_clipped', 'local_level', 'switching', 'mean_switching', 'mean_switching_clipped', 'three_way_mae', 'three_way_mse')


def trend(window):
    # Input is oldest first, at five-minute intervals; predict index 10.
    mt = (len(window)-1)/2
    mu = sum(window)/len(window)
    slope = sum((i-mt)*(u-mu) for i, u in enumerate(window))/sum((i-mt)**2 for i in range(len(window)))
    return mu + slope*(len(window)-mt)


def forecasts(series):
    smoothed = []
    for value in series:
        smoothed.append(value if not smoothed else (2/21)*value+(19/21)*smoothed[-1])
    past_errors = deque(maxlen=20)
    past_pairs = deque(maxlen=20)
    past_model_errors = deque(maxlen=20)
    pending = deque()
    for issue in range(9, len(series)-1):
        # Strictly earlier resolved targets only, mirroring the simulator learner.
        while pending and pending[0][0] < issue:
            target, old_raw, old_latest, old_trend, old_pair, old_mean = pending.popleft()
            past_errors.append((old_raw-old_latest, series[target]-old_latest))
            past_pairs.append((old_latest, series[target]))
            past_model_errors.append((abs(old_trend-series[target]),abs(old_pair-series[target]),abs(old_mean-series[target]),abs(old_latest-series[target])))
        latest = series[issue]
        raw = trend(series[issue-9:issue+1])
        denominator = sum(d*d for d, y in past_errors)
        weight = max(0, min(1, sum(d*y for d, y in past_errors)/denominator)) if len(past_errors) >= 10 and denominator > 1e-12 else .5
        # Additional untuned comparator: learn a one-step current-to-next mapping.
        # Bounded slope prevents unstable extrapolation; not a novelty claim.
        level = latest
        if len(past_pairs) >= 10:
            mx = sum(x for x, y in past_pairs)/len(past_pairs)
            my = sum(y for x, y in past_pairs)/len(past_pairs)
            variance = sum((x-mx)**2 for x, y in past_pairs)
            if variance > 1e-12:
                slope = max(0, min(1, sum((x-mx)*(y-my) for x, y in past_pairs)/variance))
                level = max(0, min(1, my+slope*(latest-mx)))
        adaptive = latest+weight*(raw-latest)
        choose_pair = len(past_model_errors) >= 10 and sum(x[1] for x in past_model_errors) < sum(x[0] for x in past_model_errors)
        recent = series[max(0,issue-19):issue+1]
        mean = sum(recent)/len(recent)
        choose_mean = len(past_model_errors) >= 10 and sum(x[2] for x in past_model_errors) < sum(x[0] for x in past_model_errors)
        candidates = [latest, adaptive, mean]
        mae_choice = mse_choice = 0
        if len(past_model_errors) >= 10:
            mae_scores = [sum(h[i] for h in past_model_errors) for i in (3,0,2)]
            mse_scores = [sum(h[i]**2 for h in past_model_errors) for i in (3,0,2)]
            mae_choice = min(range(3),key=lambda i:mae_scores[i])
            mse_choice = min(range(3),key=lambda i:mse_scores[i])
        predictions = dict(persistence=latest, mean20=sum(recent)/len(recent), median20=statistics.median(recent),
                           ema20=smoothed[issue], ols=raw, ols_clipped=max(0,min(1,raw)), half=latest+.5*(raw-latest),
                           adaptive=adaptive, adaptive_clipped=max(0,min(1,adaptive)), local_level=level,
                           switching=level if choose_pair else adaptive,
                           mean_switching=mean if choose_mean else adaptive,
                           mean_switching_clipped=max(0,min(1,mean if choose_mean else adaptive)),
                           three_way_mae=candidates[mae_choice],three_way_mse=candidates[mse_choice])
        yield issue+1, series[issue+1], predictions
        pending.append((issue+1, raw, latest, adaptive, level, mean))


def evaluate(groups):
    totals = {m: dict(n=0, absolute=0., square=0., bias=0., tp=0, fp=0, fn=0, tn=0,
                     early_tp=0, early_fp=0, early_fn=0, early_tn=0) for m in MODELS}
    host_scores = []
    for names, series in groups:
        absolute = {m: 0. for m in MODELS}
        count = 0
        for target, actual, predictions in forecasts(series):
            count += 1
            for model, estimate in predictions.items():
                error = estimate-actual
                absolute[model] += abs(error)
                row = totals[model]
                row['n'] += 1
                row['absolute'] += abs(error)
                row['square'] += error*error
                row['bias'] += error
                warning, overloaded = estimate > .8, actual > .8
                row['tp' if warning and overloaded else 'fp' if warning else 'fn' if overloaded else 'tn'] += 1
                if series[target-1] <= .8:
                    row['early_tp' if warning and overloaded else 'early_fp' if warning else 'early_fn' if overloaded else 'early_tn'] += 1
        host_scores.append(dict(traces=names, count=count, mae_pp={m:100*e/count for m, e in absolute.items()}))
    for row in totals.values():
        row['mae_pp'] = 100*row.pop('absolute')/row['n']
        row['rmse_pp'] = 100*math.sqrt(row.pop('square')/row['n'])
        row['bias_pp'] = 100*row.pop('bias')/row['n']
        row['precision'] = row['tp']/(row['tp']+row['fp']) if row['tp']+row['fp'] else None
        row['recall'] = row['tp']/(row['tp']+row['fn']) if row['tp']+row['fn'] else None
        row['early_precision'] = row['early_tp']/(row['early_tp']+row['early_fp']) if row['early_tp']+row['early_fp'] else None
        row['early_recall'] = row['early_tp']/(row['early_tp']+row['early_fn']) if row['early_tp']+row['early_fn'] else None
    return dict(summary=totals, hosts=host_scores)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--days', nargs='+', default=['20110303', '20110306', '20110309', '20110322', '20110325'])
    parser.add_argument('--label', default='complete_sequences')
    parser.add_argument('--fresh-days', nargs='*', default=['20110325'])
    parser.add_argument('--replay-inputs', type=Path, help='Evaluate retained inputs into a separate bounded-controls report')
    parser.add_argument('--synthetic', action='store_true', help='Include fixed-placement synthetic inputs in a replay study')
    parser.add_argument('--stress', action='store_true', help='Also evaluate top16 whole-day-mean traces; retrospective stress group')
    args = parser.parse_args()
    if args.replay_inputs:
        report = {}
        for inputs in sorted(args.replay_inputs.glob('*_inputs.csv.gz')):
            with gzip.open(inputs, 'rt') as stream:
                records = list(csv.reader(stream))[1:]
            groups = [(row[1].split(';'), list(map(float,row[2:]))) for row in records]
            report[inputs.name.removesuffix('_inputs.csv.gz')] = evaluate(groups)
        assert report
        if args.synthetic:
            paths = (CODE/'src/main/resources/workloads/diurnal.txt').read_text().splitlines()
            traces = [[float(x)/100 for x in (CODE/p).read_text().split()] for p in paths if p.strip()]
            groups = [(paths[i:i+4], [.25*sum(t[k] for t in traces[i:i+4]) for k in range(288)]) for i in range(0,len(traces),4)]
            report['synthetic_diurnal'] = evaluate(groups)
        filename = 'simple_baseline_controls.json'
        (args.replay_inputs/filename).write_text(json.dumps(report,indent=2)+'\n')
        for day, result in report.items():
            print(day,' '.join(f'{m}={r["mae_pp"]:.3f}' for m,r in result['summary'].items()))
        return
    assert all(d.isdigit() and len(d) == 8 for d in args.days)
    assert re.fullmatch('[a-z0-9_]+', args.label)
    root = CODE/'results/predictor_investigation'/args.label
    root.mkdir(parents=True, exist_ok=True)
    plan = root/'plan.json'
    assert not plan.exists(), 'Do not overwrite a completed/prepared study'
    plan.write_text(json.dumps(dict(recorded_utc=datetime.now(timezone.utc).isoformat(), days=args.days,
        fresh_days=[d for d in args.days if d in args.fresh_days], reused_days=[d for d in args.days if d not in args.fresh_days],
        settings='10 CPU readings; 300s horizon; adaptive memory20/min10/initial0.5/weight0..1; local-level comparator memory20/min10/slope0..1/output0..1; no tuning',
        selector='Use candidate with lower absolute error over last20 eligible resolved forecasts; min10, initial adaptive trend; candidates saved when issued',
        mean_selector='Compare adaptive trend with average of up to20 most recent CPU readings, minimum10 available; choose lower prior absolute error, memory20/min10/initialtrend; fixed before new-day outputs',
        stress='Top16 whole-day-mean traces in groups of4; load-selected, not model-selected; report separately, never pool with population groups' if args.stress else None,
        groups='All valid traces sorted by name in groups of four; each VM has one quarter of host CPU capacity; partial final group has fewer VMs',
        scope='All 278 eligible next readings per fixed host; no migration/finish/admission or energy simulation; research forecast isolation only',
        source_commit=COMMIT, archive_sha256=DIGEST), indent=2)+'\n')
    archive = CODE/'target/planetlab-source.zip'
    archive.parent.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        urlretrieve(f'https://codeload.github.com/beloglazov/planetlab-workload-traces/zip/{COMMIT}', archive)
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == DIGEST
    report = {}
    with zipfile.ZipFile(archive) as source:
        for day in args.days:
            pool, hashes = {}, {}
            for member in source.infolist():
                parts = PurePosixPath(member.filename).parts
                if member.is_dir() or len(parts) != 3 or parts[1] != day:
                    continue
                data = source.read(member)
                values = [float(x)/100 for x in data.decode().split()]
                assert len(values) == 288 and all(0 <= x <= 1 for x in values)
                pool[parts[2]] = values
                hashes[parts[2]] = hashlib.sha256(data).hexdigest()
            assert pool, day
            names = sorted(pool)
            groups = []
            for start in range(0, len(names), 4):
                members = names[start:start+4]
                groups.append((members, [.25*sum(pool[n][i] for n in members) for i in range(288)]))
            with gzip.open(root/f'{day}_inputs.csv.gz', 'wt', newline='') as stream:
                writer = csv.writer(stream)
                writer.writerow(['host','traces'] + [f'cpu_{i*300}s' for i in range(288)])
                for host, (members, values) in enumerate(groups):
                    writer.writerow([host,';'.join(members)]+values)
            (root/f'{day}_trace_hashes.json').write_text(json.dumps(hashes, indent=2)+'\n')
            results = evaluate(groups)
            results['trace_count'] = len(names)
            results['host_count'] = len(groups)
            report[day] = results
            print(day, len(groups), 'hosts;', ' '.join(f'{m}={r["mae_pp"]:.3f}' for m,r in results['summary'].items()), flush=True)
            if args.stress:
                heavy = sorted(pool, key=lambda n: (-sum(pool[n]),n))[:16]
                stress = [(heavy[i:i+4], [.25*sum(pool[n][k] for n in heavy[i:i+4]) for k in range(288)]) for i in range(0,16,4)]
                report[day+'_stress'] = evaluate(stress)
                with gzip.open(root/f'{day}_stress_inputs.csv.gz', 'wt', newline='') as stream:
                    writer = csv.writer(stream)
                    writer.writerow(['host','traces'] + [f'cpu_{i*300}s' for i in range(288)])
                    for host, (members, values) in enumerate(stress): writer.writerow([host,';'.join(members)]+values)
                print(day,'stress;', ' '.join(f'{m}={r["mae_pp"]:.3f}' for m,r in report[day+'_stress']['summary'].items()), flush=True)
            (root/'accuracy.json').write_text(json.dumps(report, indent=2)+'\n')


if __name__ == '__main__':
    main()
