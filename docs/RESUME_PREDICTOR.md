# Current predictor checkpoint

Resumed after user authorization. Framework repair verified with original/repaired map regression and complete normal-consolidation runs. Runtime patch isolation guard implemented and tested. Fresh-day 20110306 accuracy tested with frozen settings; candidate beats OLS in three of four fresh real cases, but not every baseline. Energy still does not beat IQR in the heavy case. See FRAMEWORK_REPAIR.md and FORECAST_ACCURACY.md.

Next research: source versus destination decision accounting, prediction-trigger precision/recall and benefit of actual migrations. Do not retune on the fresh holdout and continue calling it untouched validation. Preserve PR2 and its evidence. Unrelated untracked active traces and maintenance-check file were not modified.
