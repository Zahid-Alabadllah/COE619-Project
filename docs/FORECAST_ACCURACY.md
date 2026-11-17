# Forecast accuracy investigation (separate from PR2)

## What changed

Experimental model: latest CPU observation plus half the OLS-predicted change. It remains a simple trend predictor, not a novel algorithm claim. Window and blend weight were selected using the earliest 70% of matched real_a forecast targets only; the other workload cases and later real_a targets were excluded from that parameter selection. Original records are retained. This is a single tuning/validation exercise, not nested validation or proof of generalization.

## Accuracy results

Mean absolute error (MAE) is average absolute prediction error in CPU percentage points. Smaller is better. All three models in each row are evaluated against the same observed outcomes and input windows. Persistence predicts the latest reading will stay unchanged.

| Validation case | Matches | Persistence MAE | Original OLS MAE | Half-trend MAE |
|---|---:|---:|---:|---:|
| diurnal | 327 | 3.826 | 4.664 | 3.993 |
| real_a | 420 | 3.187 | 2.823 | 2.870 |
| real_b | 1395 | 2.895 | 2.867 | 2.707 |
| real_c | 1395 | 2.238 | 2.341 | 2.173 |
| heavy | 408 | 4.199 | 4.324 | 4.015 |

Half-trend improves over original OLS on four of these five evaluation segments, but does not beat persistence in the diurnal case or original OLS on late real_a. These inputs were previously used in energy exploration, so only the current parameter selection, not the entire project, held them out.

## Measurement limits and framework issue

Measurements are for matched host-level allocated-CPU forecasts when the policy attempted prediction, not every timestamp or all overload states. Future readings must match the target within one second and are excluded across completed migration generation changes. Destination probes can also invoke the predictor. This is observed-policy conditional accuracy, not a counterfactual estimate of no-migration demand.

A damped-policy run exposed CloudSim Plus 8.0.0's immutable empty-map path: getMigrationMapFromOverloadedHosts returns Collections.emptyMap when there are no overloaded hosts, while updateMigrationMapFromUnderloadedHosts subsequently calls putAll. The isolated check sets the underload threshold to 1e-9 for all policies, rather than claiming this is a production repair. Normal underloaded-host consolidation remains to be validated. No energy-superiority conclusion is drawn from that changed environment.

Default algorithm behavior has not been switched. All research options are opt-in. PR2 and results/experiments.csv are unchanged.

## Reproduce analysis

Run scripts/analyze_forecast_accuracy.py for the original replay, or use --log damped-isolated.log --output damped_isolated_accuracy.json --locked for the locked-parameter simulation analysis. Logs may be compressed; the script reads the .gz fallback automatically. Selection happens before validation metrics are generated. Raw inputs and observed targets are retained in the compressed logs.

Verified the Java forecast math on an exact line with irregular times, reversed sample order and repeated timestamps. The irregular-time fit is mathematically valid; history selection is an evaluated design choice, not an arithmetic correction.

Next: fresh workload days, threshold-crossing precision/recall, rolling validation, prediction timing separate from destination probes, and a framework repair before end-to-end promotion. A forecast gain alone does not guarantee an energy gain.

## Follow-up

A narrow opt-in repair now restores the normal underloaded-host path. A different-day validation has also completed without retuning. See FRAMEWORK_REPAIR.md for regression checks, fresh-day accuracy and unresolved energy results. Earlier isolated results remain historical rather than being overwritten.
