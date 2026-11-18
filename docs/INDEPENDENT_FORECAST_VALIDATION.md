# Independent workload validation and uncertainty checks

Recorded 18 November 2026. These are forecasting experiments, not new energy claims or extra default simulator scenarios.

## What was held fixed

The trend/mean predictor and its settings were frozen before the first new-provider evaluation. No tuning used that provider's outcomes before the first test. The existing five Java workload cases remain unchanged. This is an independent-provider check of normalized CPU forecasts; it does not reproduce that provider's heterogeneous VM sizes or datacenter placement.

## Source and acknowledgement

Data are from Bitbrains IT Services, distributed by the [Grid Workloads Archive, GWA-T-12](https://atlarge-research.com/gwa-t-12/). Credit Siqi Shen, Vincent van Beek and Alexandru Iosup, *Statistical Characterization of Business-Critical Workloads Hosted in Cloud Datacenters*, CCGrid 2015; [free paper PDF](https://atlarge-research.com/pdfs/2015-shen-statistical.pdf). The source requests acknowledgement in published material using its traces.

The fastStorage archive contains 1,250 VM CSVs. The download is temporary; its exact SHA-256, selected original-file hashes and frozen model-source hashes are retained. Only selected normalized CPU inputs and forecast evidence are retained in the project. Time values in these files are Unix seconds despite the header's millisecond label; normalization records that explicitly.

Select by numeric VM identifier and completeness, never by CPU load or forecast outcome. Inspect time/format eligibility for the first 128 identifiers; evaluate the first 76 with a common complete UTC day. Each day has 288 unique five-minute bins with adjacent sample intervals 300 seconds plus/minus one. No interpolation or missing-value filling is allowed. Divide CPU usage percentage by100. Combine four normalized VM traces per fixed host with equal quarter-capacity shares.

## First independent-provider result

The selected date is 13 August 2013, with 76 VMs and19 host groups (5,282 forecasts). The prior trend/mean model beats OLS but narrowly loses to unchanged CPU in MAE and has larger occasional errors.

| Model | MAE (CPU pp) | RMSE (CPU pp) |
|---|---:|---:|
| Unchanged CPU | 0.19480 | 0.74788 |
| OLS | 0.27624 | 1.07425 |
| Adaptive trend | 0.19944 | 0.77522 |
| Trend/mean selector | 0.19657 | 0.95701 |

MAE is average absolute miss. RMSE gives larger misses more weight. A model can improve one and worsen the other. This result limits earlier claims of generalization from PlanetLab; the model is not universally better than a simple baseline.

## Separate-VM validation of the next idea

After that result, screened a three-choice selector offline: unchanged CPU, adaptive trend and rolling mean. It uses the same20 earlier errors/minimum10, starts with unchanged CPU and prefers it on tied scores. Absolute-error selection is the primary candidate; squared-error selection is a predeclared sensitivity check. Neither was added to Java.

Freeze a new plan before parsing numeric identifiers129–256. Evaluate the first76 eligible identifiers on their earliest common complete day,14 August. They are disjoint from the first evaluated VMs. The first dataset outcome informed this design, but these separate VM outcomes did not. The dates/cohorts differ, so absolute error sizes should not be compared as if the workloads were identical.

| Reserved-VM result | MAE (CPU pp) | RMSE (CPU pp) |
|---|---:|---:|
| Unchanged CPU | 1.67820 | 7.94638 |
| OLS | 1.92904 | 7.31753 |
| Adaptive trend | 1.74892 | 7.20554 |
| Prior trend/mean | 1.76298 | 7.06322 |
| Three-choice MAE selection | 1.69134 | 7.24895 |
| Three-choice MSE selection | 1.83670 | 7.37079 |

The primary selector reduces error versus the prior trend/mean in MAE but worsens RMSE, and still misses unchanged CPU's MAE. There is no all-metric win. The extra runtime model was not adopted. **994 archive VM files remain unparsed** for later designs. Other parsed-but-unevaluated VMs and later dates must be described accurately rather than assumed completely uninspected.

Exact frozen forecasting-source snapshots match the SHA-256 fingerprints in both studies. Later bounded-output controls are explicitly post-hoc analyses, not a replacement for those first-use outcomes.

## Small correction kept in active code

The scored target is allocated CPU relative to physical capacity, whose range here is0–100%. The experimental mean selector now projects its final forecast into that interval. This cannot increase absolute or squared error against an outcome inside the interval. It is not a claim that unconstrained workload demand can never exceed physical capacity.

The internal candidate histories and fixed80% overload rule remain unchanged, and projection preserves that warning decision. A complete15-row corrected-power simulator CSV was byte-identical before/after the bound, including energy, moves and trigger counts. Independent replay verified all6,341 emitted weights/forecasts and all mean inputs; all30 power audits in that extra batch were zero. The original OLS reference mode remains available as before; bounded OLS controls are included offline for fair comparison.

Post-hoc bound checks lower trend/mean MAE from1.76298 to1.72225 on the reserved cohort, still above unchanged CPU. Clipping fixes impossible allocated-CPU outputs; it does not establish the best model or save energy.

## Forecast uncertainty is not free improvement

Using the last20 strictly earlier forecast residuals, tested nonnegative80th/90th empirical-quantile margins around the frozen point forecast. These are recent-error margins, not confidence-interval guarantees. All input days were previously inspected; this is exploratory.

On9April's fixed heavy sequences, point warnings catch about40% of new80% crossings with28 false warnings. The80th-percentile margin catches about75% but produces186 false warnings. On20April, the corresponding trade-off is about63%/27 false versus72%/39 false. Larger margins also increase false warnings. Recorded upper-bound coverage is below the nominal quantile levels.

This was not connected to migrations. More warnings can mean more unnecessary moves and powered hosts; better recall alone is not proof of a better consolidation policy.

## Evidence and next step

- bitbrains_holdout: frozen initial plan, original outcomes, normalized inputs and exact model-source snapshot.
- bitbrains_reserved_vms: separate frozen plan, disjoint-VM outcomes, post-hoc controls and exact snapshot.
- forecast_margins: causal uncertainty-margin plan and results.
- online_holdout_20110420/bounded_power_mean: actual bounded Java model, replay and power checks.

Use reserved VM outcomes only after freezing the next design and its primary loss metric. Keep the two-choice bounded predictor as an experimental reference, not a proven universal winner. Energy/performance usefulness remains a separate test under the corrected power model. PR2 stays frozen with its power-accounting erratum.
