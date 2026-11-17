# Complete-sequence forecasting study, 18 November 2026

Earlier accuracy measurements included only simulator forecasts that could be matched later. This study additionally checks every eligible next reading in fixed host sequences. Four real VM traces share a host; each contributes one quarter of host CPU capacity. There are 278 targets per host after the first ten samples. These sequences do not model cloudlet completion or migration and cannot establish energy savings.

All available valid traces on each selected day are sorted into fixed groups of four, including a smaller final group when necessary. Separate heavy groups use the 16 highest whole-day mean traces. Heavy selection is retrospective by load, not model outcome. Keep stress and population results separate. Source commit/digest, per-trace hashes, compressed aggregate inputs, model settings and results are retained.

## Recent-pair comparator

Plain meaning: learn what CPU reading usually follows a particular current reading. This is a bounded rolling one-lag autoregression-style comparator, not a new algorithm claim. Ordinary autoregression fitted by OLS is documented in [the official AutoReg reference](https://www.statsmodels.org/stable/generated/statsmodels.tsa.ar_model.AutoReg.html). Our 20-pair memory, ten-pair warmup, coefficient/output bounds and strictly earlier-target rule are project choices; we do not use statsmodels at runtime.

For earlier pairs `(x,y)`, fit `next = mean(y) + b*(current - mean(x))`, where `b = covariance(x,y)/variance(x)` is bounded to [0,1]. The forecast is bounded to [0,1]. Until ten earlier verified pairs exist, predict unchanged CPU. A zero-variance fit also falls back to unchanged CPU.

An additional selector chooses between this comparator and the adaptive time trend using their last 20 earlier absolute forecast errors, requiring ten before switching. It records each candidate forecast when issued; it does not refit a past forecast after seeing the outcome.

| Complete workload day | Adaptive trend MAE (pp) | Recent-pair MAE (pp) |
|---|---:|---:|
| 2011-03-03, reused | 3.366 | 2.879 |
| 2011-03-06, reused | 3.291 | 2.808 |
| 2011-03-09, reused | 3.555 | 3.104 |
| 2011-03-22, reused | 3.005 | 2.596 |
| 2011-03-25, new at first test | 3.140 | 2.696 |
| 2011-04-03, additional unchanged-model check | 3.458 | 2.976 |
| 2011-04-09, new for the selector check | 3.490 | 3.031 |
| 2011-04-11, new stress/population check | 3.562 | 3.071 |

On 9 April, recent pairs improve 337 of 340 fixed host groups against adaptive trend, losing on three. The synthetic fixed-placement case is an exception: adaptive trend 3.265 pp versus recent pairs 4.003 pp. These are explicit tested workloads, not independent population samples or a universal superiority claim.

The simulator also completed pair/selector checks on the March validation cases and new April groups, with independent causal event replay and standalone Java checks. The models improve real-data point accuracy but do not demonstrate predictive energy savings; the heavy simulator comparisons still have no predictive overload flags. Synthetic pair accuracy is worse. Original evidence and PR2 are unchanged.

## Stronger simple baselines change the recommendation

Additional baseline controls were added after the initial study, not secretly predeclared. On the 9 April population sequences, rolling mean/median/exponential smoothing (span20) score 3.004/2.961/2.981 pp, compared with recent-pair 3.031. On the 11 April heavy sequence, rolling mean is 7.060 versus recent-pair 7.181. Merely bounding OLS outputs does not explain the gains: clipped and unclipped OLS are close.

Thus the extra regression is not needed to claim a benefit over simpler alternatives. A trend-versus-rolling-mean selector is the next simpler candidate. In reused replay it scores 3.070 pp on 9 April and 3.278 on synthetic, compared with adaptive 3.490/3.265 and rolling mean 3.004/6.108. It offers a balance rather than beating every specialist. No model change should be promoted based only on the initial weaker comparisons.

## Overload-event limits

Normal population groups on the inspected April days have no >80% events, so their event precision/recall is undefined. Do not interpret absent events as perfect overload prediction.

On 11 April's four load-selected heavy groups, 116 new crossings from <=80% to >80% occur. OLS warns before 25 with 36 false warnings; adaptive trend before 20 with 24 false warnings; recent pairs before 21 with 25 false warnings. Better average error does not solve most sudden crossings.

## VM aggregation check

Forecasting each VM and adding its predicted share gives only a small extra point-accuracy gain: 11 April host-pair MAE 3.071 versus VM-pair 3.066; unused 12 April 3.030 versus 3.020. Early warning results are mixed across the two stress sets. No Java VM-level implementation was added for this marginal gain. Exact replay code, frozen plan and metrics remain in vm_aggregation.

## Reproduction and scope

Use scripts/research/validate_complete_sequences.py with a new label to prepare a new study; it refuses to overwrite its plan. The replay-inputs option evaluates retained aggregate inputs. compare_vm_aggregation.py retains a separate immutable plan. The internal key local_level refers to the recent-pair comparator above, not a fitted state-space local-level model.

Repeated and previously inspected days are reuse checks after later design changes. Only the first evaluation of unchanged settings on an unused day is new validation. Next: freeze and evaluate the simpler mean selector before seeing another day. Keep point accuracy, early warning accuracy and end-to-end energy as separate claims.
