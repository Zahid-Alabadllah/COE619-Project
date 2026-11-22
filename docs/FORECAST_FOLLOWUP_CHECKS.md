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

## 23 November - Migration timing and bounded deferral
Added opt-in decision diagnostics without changing policy behavior: reactive checks show CPU, time and available samples; completed moves show source, destination and timing. The diagnostic-only15-row CSV is byte-identical to the prior robust run. In the full-day heavy case, THR/robust move at600.1s (only two recent samples available) and7800.1s; IQR's only move starts1500.1s. Cold-start behavior and server active time therefore matter more than point-error gains alone.

Tested a separate bounded rule: once ten CPU readings exist, if current CPU is above80% but below95% and the robust forecast is at most80%, wait one five-minute bucket at most; move if overload persists into the next bucket.95% is an illustrative design ceiling, not an SLA guarantee. Cold-start behavior remains THR. The control waits one bucket without consulting the forecast, under the same warmup/ceiling. Both were opt-in and are now retired.

| Full-day heavy case | kWh | Completed moves | Active host-hours |
|---|---:|---:|---:|
| Original robust/THR |25.707605|2|120.3333|
| Forecast deferral |25.708297|1|120.3333|
| Delay without prediction |25.708297|1|120.3333|
| IQR |25.663730|1|120.0833|

The simple delay matches the forecast rule's heavy outcome exactly. No incremental forecasting benefit was established. The migration reduction does not save energy and does not switch off a server. Sampled unmet demand and saturation remain zero, which is not a universal performance guarantee. The delay control slightly lowers synthetic energy; that is a timing-rule result, not a prediction result.

With active-host power175+75u watts, a0.25 host-hour difference contributes0.04375kWh in the175W fixed component alone, close to the observed0.043875kWh heavy gap versus IQR. This decomposition supports investigating active-host duration and placement feasibility. It does not prove which small dynamic-power/timing terms explain the remainder.

Both deferral runs pass independent robust forecast replay (6,343/6,340 checks), closed coverage ledgers and30 zero power audits each. The exact experimental patch againstd976ceb is scripts/research/attempts/bounded_forecast_deferral.patch. Logs, run options and15-row CSVs remain under online_holdout_20110420/robust_bounded_defer and robust_one_step_control. Active code retains useful diagnostics but removes the two unsuccessful policy options.

The offline trend-switching extension improves synthetic MAE3.8588 to3.3148pp in fixed-placement replay but worsens the real/cohort comparisons, so it was not added to Java. Offline brief-overload analysis has six unique cohort keys plus synthetic; an initial basename-key collision was corrected before drawing conclusions. All inputs are now reused development data, with no further reserved VM files opened. See forecast_decision_audit for full scores and the19/22 short-spike outcomes; these are next-reading observations, not energy or safety proofs.

## 23 November - No-migration reference and a lifecycle benchmark
The full-day fixed-population reference disables all migration policies. Labels THR/IQR/LR are retained by the runner but no policy acts. In that setting, no migration uses10.240151kWh on day/night and21.508036kWh on heavy, below robust/THR12.719158 and25.707605. Quiet real groups are unchanged. There is no counted saturation or sampled unmet demand. This exposes a benchmark limitation, not a general theorem that consolidation is harmful: VMs never finish in the full-day study and already start tightly packed.

A separate controlled workload now gives three out of every four VM indices a short5000000MI-per-PE job and the fourth the long1000000000MI-per-PE job. Real CPU traces, VM order and resource sizes remain unchanged. Completed VMs release their resources after1second, using the CloudSim Plus broker destruction API. These job lengths and lifetimes are synthetic benchmark choices, not measured PlanetLab VM lifetimes. They apply equally to THR, IQR, robust LR and the no-migration reference. There are no new arrival patterns or random seeds.

Initial fairness checks found unequal event-tail end clocks and small nonzero sampled CPU shortfall during migrations. The final mixed-job benchmark therefore clips energy, active-host/saturation time and sampled demand to0..86400seconds; completed-job counts also use that horizon. Interpolated power intervals are clipped at the boundary. end_time_s still reports the actual simulation clock so tail differences remain visible. PDM retains its documented execution-time/capacity approximation. The original benchmark's measurement behavior is unchanged.

Final24-hour results, robust LR versus no migration:

| Case | Consolidation kWh | No-migration kWh | Reduction | Sampled unmet-demand fraction |
|---|---:|---:|---:|---:|
| Day/night |6.000527|9.004668|33.36%|0|
| Real a |19.966631|21.446918|6.90%|0|
| Real b |21.487827|21.487827|0%|0|
| Real c |17.460824|21.528711|18.90%|0|
| Heavy |10.837014|18.186062|40.41%|0.0000794651|

Every comparison has the same created-VM and completed-job counts. Heavy finishes12 short jobs in both; sampled unmet demand is about0.008% with consolidation versus zero without. SLATAH is zero, which must not hide that sampled shortfall. Both final batches pass30 zero power checks each. These are controlled-case savings, not statistical population estimates or proof of no performance cost.

THR and robust LR match in every mixed-job case. IQR's heavy energy is10.824817kWh, slightly lower. Thus this benchmark demonstrates useful consolidation but not extra value from prediction. Original OLS and the previous fixed five cases remain available unchanged. The final original-benchmark repeat is byte-identical to the saved robust reference.

Validated results: online_holdout_20110420/mixed_jobs_windowed and mixed_jobs_windowed_no_migration. Earlier mixed_jobs_robust and mixed_jobs_no_migration contain preliminary unbounded-tail measurements and are superseded for fair energy comparison. no_migration_reference is the separate fixed-population diagnostic, not the lifecycle control.

```powershell
./scripts/research/run_forecast_validation.ps1 -Day 20110420 -Model robust_guard -Label new_mixed_test -StablePlanning -FullDay -AuditPower -MixedJobs
./scripts/research/run_forecast_validation.ps1 -Day 20110420 -Model control -Label new_mixed_reference -StablePlanning -FullDay -AuditPower -MixedJobs -NoMigrations
```

Use new labels to preserve earlier evidence. The next targeted question is whether forecast-aware source selection or shutdown timing adds anything beyond an equally informed reactive rule. The library's isUnderloaded is used for both source selection and destination filtering; modifying it globally can accidentally exclude all candidate destinations. Investigate those roles separately before another policy change. No further reserved Bitbrains outcomes have been used.
