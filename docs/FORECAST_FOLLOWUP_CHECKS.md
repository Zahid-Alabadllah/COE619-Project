# Bounded forecasting follow-up

Recorded 18 November 2026. These checks use previously inspected inputs and are exploratory, not new independent validation. No new Java model was adopted.

## Persistence blend

Tested moving partway from unchanged CPU toward bounded adaptive trend, learning that amount from the last20 strictly earlier verified errors. It starts with unchanged CPU until ten errors are available. Objective is squared error; report MAE too.

On the first Bitbrains block, the blend has MAE0.1926/RMSE0.7657pp versus persistence0.1948/0.7479. MAE is slightly better, but larger-error performance is worse. On the separate block it has1.6594/7.2286 versus bounded adaptive1.6686/7.1532, again a trade-off. It also trails the existing bounded trend/mean selector on the reused PlanetLab population/stress inputs. The blend was not promoted.

## Two-reading regression

Tested forecasting from the last two readings rather than one, with20 earlier training triples and a small ridge penalty toward unchanged CPU. Warmup uses unchanged CPU; outputs remain within0–100%. The fixed penalty is a design choice, not a tuned universal constant. This is a conventional autoregression-style experiment, not a novelty claim.

It scored MAE/RMSE0.2198/1.0157pp on the first Bitbrains block and2.0560/7.6623 on the separate block. These do not beat the simpler controls. It also trails the existing selector on the reused PlanetLab sets. Do not add extra coefficients to the runtime merely because this model was tried.

Plans, exact settings and metrics are retained in persistence_blend and ar2_development. Scripts are research tools outside the five production Java files. No more reserved VM outcomes were consumed. Next model choice should have a primary loss metric fixed before using reserved data; MAE, tail error, warning precision and energy are separate objectives.

## 20 November - Absolute-error blend and workload diagnosis
Tested a conservative blend of the current bounded trend/mean candidate with persistence. The weight minimizes the last 20 strictly earlier resolved absolute errors; it starts at persistence until ten examples exist. This is an exploratory implementation choice, not a novelty claim. The weighted-median optimizer matched exhaustive breakpoint minima in 33 checks. No additional reserved VM files were opened.

MAE / RMSE in CPU percentage points:

| Inspected input | Persistence | Current bounded selector | Absolute-error blend |
|---|---:|---:|---:|
| Bitbrains first group | 0.1948 / 0.7479 | 0.1959 / 0.9562 | 0.1828 / 0.8345 |
| Bitbrains second group | 1.6782 / 7.9464 | 1.7223 / 7.0392 | 1.7048 / 7.3221 |
| PlanetLab 20 April, population | 4.6395 / 6.8907 | 3.6899 / 5.3043 | 3.7576 / 5.4554 |
| PlanetLab 20 April, heavy group | 8.3856 / 10.6575 | 6.5828 / 8.3676 | 6.6834 / 8.5008 |

The blend improves typical error in the first Bitbrains group but worsens its large-error score versus persistence. It does not beat persistence MAE in the second group and regresses versus the current selector on PlanetLab. It stays offline; production Java is unchanged.

Retrospective diagnosis: CPU is below 10% at 98.3% and 92.8% of forecast issue moments in the two Bitbrains groups. The largest 1% of squared errors accounts for 92.2% and 63.9% of the current selector's total squared error. For the PlanetLab population, that share is 18.0%. These descriptions explain why average and tail metrics disagree; they do not prove unpredictability or supply information available at forecast time. No live decision may use the next observed change.

Evidence: results/predictor_investigation/absolute_blend_development contains the pre-run plan, input hashes, exact frozen source, full scores, host scores and regime diagnostics. This is reuse, not untouched validation, and makes no energy claim. Future work should specify whether typical CPU error or missed overloads is the primary target before opening more data, rather than selecting a metric after a run.
