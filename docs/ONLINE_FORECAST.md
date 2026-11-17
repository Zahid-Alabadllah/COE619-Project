# Adaptive forecast investigation

Recorded: 18 November 2026. Research only; PR2 is unchanged.

The predictor can learn how much to trust its straight-line trend from its own earlier errors. If trends have been misleading, it moves closer to predicting that CPU stays unchanged. It uses only outcomes whose target time is strictly earlier than the new forecast's issue time. No future readings enter the forecast.

For each host separately, the experimental blend weight minimizes squared error using the last 20 available past matched errors. It stays between zero (hold the latest value) and one (use the full OLS forecast). Until ten past errors exist, it uses the previous half-trend weight. The memory length was selected from 20, 50, 100 and unlimited using early original-day real_a only. This is an engineering candidate, with no novelty claim.

## Recorded replay results

Mean absolute error is the average distance between predicted and observed CPU, in percentage points. Smaller is better.

| Case | Hold latest | Original OLS | Half-trend | Adaptive blend |
|---|---:|---:|---:|---:|
| Original day: diurnal | 3.878 | 4.201 | 3.842 | 3.621 |
| Original day: late real_a | 3.188 | 2.823 | 2.870 | 2.855 |
| Original day: real_b | 2.895 | 2.867 | 2.707 | 2.666 |
| Original day: real_c | 2.238 | 2.341 | 2.173 | 2.185 |
| Original day: heavy | 3.643 | 4.330 | 3.718 | 3.619 |
| Reused different day: real_a | 2.644 | 2.510 | 2.444 | 2.456 |
| Reused different day: real_b | 3.946 | 3.470 | 3.515 | 3.488 |
| Reused different day: real_c | 3.764 | 3.543 | 3.496 | 3.525 |
| Reused different day: heavy | 3.819 | 4.608 | 4.053 | 3.737 |

The different-day synthetic diurnal run repeats the same synthetic case; it is not independent evidence. Adaptive blending improves over half-trend in six of these nine distinct segments, but loses in three. It beats persistence in both recorded heavy cases. It still loses to original OLS on late real_a and different-day real_b.

## Limits and next step

The initial replay uses previously inspected logs, not fresh confirmatory validation. Targets are conditional matched allocated-host-CPU observations from the saved policy, excluding completed migration changes. Source detection and temporary source-evacuation checks can both invoke forecasting. Replay does not change migration decisions and cannot establish energy savings. Subsequent opt-in Java integration and new-day validation are recorded below.

Next: integrate behind a separate opt-in switch, verify that updates use only resolved past targets, and compare replay with simulation outputs before testing an untouched workload day. Measure energy and migrations separately.

## Simulator integration and two new workload days

The opt-in `coe619.onlineForecast` switch now enables the learner inside the existing predictor class. Normal behavior remains unchanged when research switches are absent. It learns even when diagnostic printing is off. Each host keeps its own last 20 eligible resolved errors. Targets equal to the current issue time wait until a later forecast. Completed migration changes exclude pending targets as before.

Standalone Java checks passed for cold start, future and same-time exclusion, bounded weights, rolling-memory eviction, independent state and zero-denominator fallback. Independent Python event replay verified 5,584 original-day, 5,541 first-new-day and 5,659 second-new-day logged weights, with maximum weight differences below 0.000001. Differences reflect rounded log timestamps. This verifies implementation agreement, not statistical generalization.

The model settings were frozen before testing 9 and 22 March 2011. Each day uses three disjoint fixed 20-trace groups plus 16 heaviest traces, selected without model results. Each heavy case is a retrospective load-selected stress test. Per-file digests, author-repository commit, selection rules and model settings are retained in each day's provenance. The synthetic case is repeated only as a regression check.

All models below use the same matched outcomes and inputs from the online policy's path. These are forecast comparisons, not separate policy-energy comparisons.

| Day / real case | Matches | Hold latest MAE | OLS MAE | Half-trend MAE | Online MAE |
|---|---:|---:|---:|---:|---:|
| 2011-03-09 real_a | 1400 | 3.347 | 3.122 | 3.075 | 3.065 |
| 2011-03-09 real_b | 1400 | 3.318 | 3.215 | 3.087 | 3.064 |
| 2011-03-09 real_c | 1395 | 4.117 | 3.867 | 3.792 | 3.815 |
| 2011-03-09 heavy | 404 | 3.955 | 4.754 | 4.171 | 3.857 |
| 2011-03-22 real_a | 1395 | 3.373 | 2.972 | 3.017 | 2.976 |
| 2011-03-22 real_b | 1395 | 2.719 | 2.493 | 2.487 | 2.477 |
| 2011-03-22 real_c | 1395 | 3.863 | 3.424 | 3.489 | 3.472 |
| 2011-03-22 heavy | 535 | 5.574 | 5.770 | 5.367 | 5.277 |

Online wins against holding the latest value in 8/8 cases, OLS in 6/8 and half-trend in 7/8. Across 9,319 matched real forecasts, pooled MAE is 3.599, 3.399, 3.329 and 3.298 percentage points respectively. This is about 0.9% lower error than half-trend, a modest gain. Counts in this rounded table should be checked against the machine-readable accuracy files, which are authoritative.

The model is not universally best: half-trend wins 9 March real_c, and OLS wins 22 March real_a and real_c. No confidence interval, broad population claim or novel-algorithm claim is made. Conditional 80% warning precision/recall is stored in each accuracy JSON; it does not measure every host moment or establish migration benefit.

Separate half-trend simulations, a repeated online batch and forecast-disabled controls were also completed. Both fresh heavy cases have zero predictive flags, so slightly different energy values cannot establish a predictive saving. A single-heavy-case trace run and the full batch also differ slightly in energy, motivating a separate ordering/reproducibility investigation. The original synthetic case still has six online-policy migrations versus two for THR/IQR. Do not promote this model as an energy winner.

### Plain explanation and equation

If CPU is 50% now and the line predicts 60%, a learned weight of 0.2 gives a forecast of 52%. The weight means how much of the predicted change we trust. This is our experimental design choice, not a published standard.

`forecast = latest + weight * (OLS forecast - latest)`

For each resolved earlier forecast, let `d = OLS forecast - latest` and `y = observed CPU - latest`. The fitted weight is `clip(sum(d*y) / sum(d*d), 0, 1)` over the most recent 20 eligible errors. Clip means keep it between zero and one. If fewer than ten verified errors exist, or the denominator is too small, use 0.5. The first straight-line fit still separately requires ten CPU samples.

### Reproduce and inspect

- Preparation: `python scripts/research/prepare_forecast_holdout.py --day 20110309` (refuses to overwrite prepared evidence).
- Simulation: `./scripts/research/run_forecast_validation.ps1 -Day 20110309 -Model online` (refuses to overwrite a completed run; use a new Label for repeats).
- Check: `python scripts/research/check_online_forecast.py results/predictor_investigation/online_holdout_20110309/online/simulation.log.gz target/online-check.json`.
- Other model options: `half` and `control`. The latter disables forecasting. All use the same opt-in framework repair and normal underload consolidation.

Run metadata records actual UTC times, options, Git state and hashes verifying frozen PR2/report CSV. The first manually launched integration runs predate that wrapper; their logs, parameters and preparation provenance are retained without invented start times.

Reproduce with `python scripts/analyze_online_forecast.py`. Machine-readable results: `results/predictor_investigation/online_forecast_accuracy.json`.

## Reporting timeline

Keep actual experiment, file and Git commit dates. When the Term 262 calendar arrives, group these milestones into its deliverables and explicitly identify work completed in advance. Do not change historical dates or present early work as newly performed later. Preserve separate commits and experiment evidence so improvements remain traceable.
