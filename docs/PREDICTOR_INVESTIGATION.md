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

Next: verify repeated-run stability, attribute forecast flags to actual source migrations versus destination checks, inspect history across VM moves, and measure forecast error before comparing a different predictor. Raw results and logs remain under results/predictor_investigation. Unrelated untracked files were left untouched.
