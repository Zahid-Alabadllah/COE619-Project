# Underload planning and migration-cost audit

Actual work date: 27 November 2026. This is early preparation for Term 262, outside the frozen PR2 scope.

The simulator must decide where VMs could move without moving them during that decision. The original underload path temporarily adds the real VM to another host's physical list. Our small reproducer shows the same VM listed on two hosts before a migration starts. A controlled full run also fails the final placement check. These are correctness failures, not algorithm improvements.

The opt-in numeric planner keeps a separate resource budget for each destination. It only returns a migration plan after all VMs on a source fit. Physical host lists, allocations and job execution stay unchanged during planning. This research helper is not a sixth production Java file and is not enabled by default.

Run the controlled correctness check from the code repository:

```powershell
./scripts/research/run_underload_check.ps1 -Mode repaired
```

That output already exists. To repeat without overwriting evidence, add a unique `-Label`. Modes `legacy` and `no_migrations` provide the fault reproduction and no-movement control. The legacy mode succeeds only when the expected placement failure is reproduced.

## What was verified

- Unit checks cover complete-source rollback, CPU/RAM/bandwidth/storage limits, exact threshold equality, excluded destinations, transfers in progress, existing migration reservations, multiple sources, invalid input and injected exceptions. The public optimizer dispatch is checked too.
- In the controlled case, all nine short jobs finish and all three long jobs remain present over the same 24-hour window. Each repaired policy makes two migrations. The original planner fails the placement check.
- The five-case, three-policy matrix records 11,290 planning calls with unchanged physical state, 30 zero recorded power violations and 15 clean final placement checks. This is recorded coverage, not proof of every possible simulator state.
- Repeating the repaired matrix gives identical CSV bytes. The no-migration matrix and ordinary default benchmark also match their earlier references exactly. Generated framework/planner classes are cleared after use; the dependency jar is not edited.
- Independent replay reproduces all 13,298 candidate forecasts exactly. Available target readings favor the predictor over persistence in the four real cases, but not synthetic day/night. Operational target coverage still has missing readings and interventions, so this is not complete-sequence or fresh-data validation.

The helper intentionally skips hosts with any transfer in progress and honors excluded destination sets. Those conservative choices differ from the original path. Energy differences must therefore be attributed to the common planning change, not forecasting or a perfectly equivalent library rewrite.

## Energy result

On the repaired heavy lifecycle case, THR and the predictive policy both use **10.956979 kWh**, versus **10.910594 kWh** for IQR. Each makes three migrations. Prediction has not added an energy benefit. These values also exceed the previous planner's heavy figures; fixing correctness is not an energy win.

## Migration-cost omission

PDM is our estimate of CPU work lost during VM moves. The existing approximation assumes a 10% VM-capacity penalty during RAM transfer. Its unfinished-job denominator uses the VM's reported execution time. After a migration, the framework can reset the VM start time to its stored stop time, yielding zero reported execution time even while its job runs. Our old calculation then skips that VM.

The audit reproduces every stored PDM value in all 15 rows, and finds **18 migrated VM instances** omitted by that denominator. The controlled case makes two migrations per policy but reports legacy PDM zero for this reason. This is separate from SLAV being zero when SLATAH is zero.

`calculate_windowed_pdm.py` reports a separate capacity-time estimate using the original job start and the common observation end, retaining finished-job work as before and including any transfer still in progress. Five boundary fixtures pass. It does not overwrite the historical CSV or the main metric implementation. This remains our approximation, not a measurement of exact requested CPU work.

Beloglazov and Buyya (2012) define migration degradation using estimated lost performance relative to requested performance. Attribute that research convention beside any final formula, and distinguish our capacity-time approximation from it. [Free paper](https://beloglazov.info/papers/2012-optimal-algorithms-ccpe.pdf).

## Evidence and next decision

Evidence: `results/predictor_investigation/read_only_underload/` and `online_holdout_20110420/readonly_validated_matrix/`. `verification.json` records frozen report/CSV hashes, repeated-result comparisons and audit counts. `forecast_replay.json`, `forecast_scope/` and `matrix_pdm_audit.json` retain the separate forecasting and metric results.

Next, keep this repaired planning path fixed while testing forecast-driven decisions against the same reactive control. Do not change the predictor and placement rule together or credit a shared repair to prediction. Before final performance claims, adopt and clearly label a consistent migration-cost denominator. PR2, original results and the 738 reserved Bitbrains VM files remain untouched.
