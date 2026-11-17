# Predictor investigation: initial controlled comparison

PR2 and its report CSV are unchanged. The default policy remains unchanged. An opt-in ablation (`coe619.spacedHistory=true`) prevents history entries less than 300 seconds apart from occupying adjacent window positions; it does not interpolate a regular grid. OLS continues to use actual timestamps.

Irregular timestamps are mathematically valid for OLS, so the initial observation is not proof of an arithmetic bug. The question is whether dense state events distort what the last ten observations represent. A heavy-only check and a full five-case comparison both removed the extra heavy-case LR move under the spacing ablation. More repeat runs and forecast-error measurements are needed before enabling it by default.

| Case | Original LR kWh / moves | Spaced LR kWh / moves |
|---|---:|---:|
| diurnal | 5.7720 / 3 | 5.7532 / 3 |
| real_a | 21.9444 / 0 | 21.9444 / 0 |
| real_b | 21.8661 / 0 | 21.8661 / 0 |
| real_c | 21.5899 / 0 | 21.5899 / 0 |
| heavy | 9.5048 / 5 | 8.1744 / 4 |

Observed diagnostic windows in the spaced run cover at least 2700 seconds (45 minutes). The heavy result improves against original LR but is approximately THR, not a demonstrated predictive win. Diurnal still has three LR moves; the three quiet real groups remain unchanged. No new model or novelty claim is established.

At this checkpoint, next steps were repeated-run stability, attributing forecast flags, inspecting history across VM moves and measuring forecast error. Source inspection on 18 November established that the extra predictor calls are temporary source-evacuation checks; destination placement uses a private threshold check. See ONLINE_FORECAST.md for later results. Raw results and logs remain under results/predictor_investigation. Unrelated untracked files were left untouched.

Follow-up: confirmation, migration-history reset and adaptive-threshold ablations are saved separately. Adaptive predictor and forecast-disabled control had identical results; energy gain cannot be attributed to forecasting. See FORECAST_ACCURACY.md for tuning split, matched forecast accuracy, exceptions and the framework defect. No settings promoted to default.

Paused at user request. The repaired normal-consolidation batch completed and wrote results/predictor_investigation/damped_normal_repaired/experiments.csv. Full verification is pending. The generated patched class was removed at the pause checkpoint to prevent accidental use in baseline runs. See docs/RESUME_PREDICTOR.md for next steps.
