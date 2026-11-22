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

## 20 November - Robust current-to-next CPU forecasting
After the absolute-error blend gave mixed results, tested a different hypothesis: occasional jumps distort ordinary regression of next CPU on current CPU. The robust fit uses median pairwise slopes and a joint median intercept (Theil-Sen; https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.theilslopes.html). Our slope bound0..1, CPU projection0..1, rolling20 earlier pairs, min10, and persistence fallback are project design choices. The conservative selector uses the last20 strictly earlier absolute errors, min10; warmup and ties prefer persistence. Primary endpoint was MAE; RMSE, per-host scores and80% warnings were secondary. No universal/novel method claim.

Reused development inputs: conservative robust/persistence MAE0.1750 and1.5842pp in the two Bitbrains cohorts, versus persistence0.1948 and1.6782. PlanetLab population3.5890 versus current selector3.6899; heavy6.6027 versus current6.5828, a small regression. Development data is not fresh validation. Exact-line, constant-input, outlier and191 future-perturbation causality checks passed.

Settings frozen before parsing two further disjoint blocks of128 numeric Bitbrains IDs. In each block, evaluated first76 complete VMs on earliest common complete UTC day, grouped into19 normalized hosts. Selection uses timestamps/completeness, not model outcomes. Archive hash and all source/input hashes retained. These are new VMs within a known provider, not new providers or energy simulations.

| Fresh cohort | Persistence MAE/RMSE pp | Current bounded selector | Robust/persistence candidate |
|---|---:|---:|---:|
| Third128 IDs,14August |0.95158/4.06291|1.12070/3.99194|0.94017/3.97016|
| Fourth128 IDs,13August |0.25716/1.49831|0.26520/1.42730|0.22269/1.48172|

Candidate beats persistence on both metrics in both fresh cohorts. Improvement is modest in the first. It beats the current selector's MAE in both, but loses RMSE to it in the fourth block. In the third block it wins host MAE on10/19 and loses on9/19, so aggregate improvement is not universal host improvement. No settings changed between the two fresh validations. Remaining738 archive VM files unparsed. Evidence: robust_pairs_development, bitbrains_robust_reserved and bitbrains_robust_confirm. No Java integration or energy claim at this checkpoint.

## 23 November - Opt-in simulator integration
The same robust/persistence candidate is now available only with the research option coe619.robustForecast. The five production Java files remain; the learner is nested in the existing policy. It learns only resolved targets strictly before the current issue time, keeps20 observations and resets its state after a completed migration changes the host's VM group. Offline validation has fixed groups and therefore no such migration reset. Default OLS and the prior mean selector are preserved.

The corrected-power full-day20April simulator batch completed normal consolidation. Independent Python replay matches all6,340 issued robust forecasts (maximum numerical difference below5e-13);6,293 are scored, one excluded after migration and46 at simulation end. No unaccounted forecasts remain. All30 recorded power checks are zero. Separate Java checks passed exact-line fitting, constant inputs, causal warmup, rolling memory, fallback selection and independent host state.

| Simulator case | Prior trend/mean MAE pp | Robust/persistence MAE pp |
|---|---:|---:|
| Synthetic day/night |2.873|3.320|
| Real a |3.992|3.903|
| Real b |3.726|3.694|
| Real c |5.231|5.108|
| Heavy |5.751|5.727|

These are conditional scored moments in each run; the synthetic policies can change their histories, so they are not a matched-target causal accuracy comparison. Real cases have corresponding counts; complete-sequence offline evidence is separate. The robust policy has zero predictive triggers in this batch and matches THR energy and completed migrations. Heavy IQR remains lower-energy. Better point forecasting is not an energy-saving result.

A full15-row mean-selector control was repeated after integration: CSV bytes are identical to bounded_power_mean, independent replay passes6,341 checks, and all30 power checks are zero. No need to repeat unrelated baselines. Frozen PR2 PDF and experiments.csv hashes remain unchanged. Saved runs: online_holdout_20110420/robust_power_full_day and robust_integration_mean_control.

Reproduce from the code repository:

```powershell
./scripts/research/run_forecast_validation.ps1 -Day 20110420 -Model robust_guard -Label new_robust_check -StablePlanning -FullDay -AuditPower
python scripts/research/check_robust_forecast.py results/predictor_investigation/online_holdout_20110420/new_robust_check/simulation.log.gz results/predictor_investigation/online_holdout_20110420/new_robust_check/accuracy.json
```

Use a new label; completed studies cannot be overwritten. This remains experimental because day/night accuracy regresses and predictive energy benefit is unresolved. Keep the remaining738 unparsed Bitbrains VM files for a future frozen design. Fresh validation above refers to new VM groups in the same provider, not another provider or new workload day.
