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

These are previously inspected logs, not fresh confirmatory validation. Targets are conditional matched allocated-host-CPU observations from the saved policy, excluding completed migration changes. Source decisions and destination checks remain mixed. Replay does not change migration decisions and cannot establish energy savings. The simulator still uses its existing opt-in half-trend candidate; this adaptive candidate is not integrated into Java yet.

Next: integrate behind a separate opt-in switch, verify that updates use only resolved past targets, and compare replay with simulation outputs before testing an untouched workload day. Measure energy and migrations separately.

Reproduce with `python scripts/analyze_online_forecast.py`. Machine-readable results: `results/predictor_investigation/online_forecast_accuracy.json`.

## Reporting timeline

Keep actual experiment, file and Git commit dates. When the Term 262 calendar arrives, group these milestones into its deliverables and explicitly identify work completed in advance. Do not change historical dates or present early work as newly performed later. Preserve separate commits and experiment evidence so improvements remain traceable.
