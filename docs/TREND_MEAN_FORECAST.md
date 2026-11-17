# Adaptive Trend and Mean Predictor

Research checkpoint: 18 November 2026. PR2 and its CSV remain frozen.

## What it does in plain English

Keep two simple forecasts. A line follows a rising or falling CPU trend. A rolling average ignores some of the short-lived noise. Use whichever has made smaller errors recently. At prediction time, only already verified earlier forecasts may influence that choice.

The rolling mean is the average of recent readings, dropping older ones as new readings arrive. This conventional smoothing idea is described in the [NIST moving-average reference](https://www.itl.nist.gov/div898/handbook/pmc/section4/pmc421.htm). Our window lengths, adaptive trend blend, warmup and selection rule are experimental choices, not a universal standard or a claimed new algorithm.

## Enough mathematics to explain it

1. **Trend:** `trend = latest + w * (OLS forecast - latest)`. OLS uses ten active readings, spaced at least 300 seconds apart, and evaluates its line at current time plus 300 seconds. Weight `w` is the earlier adaptive blend already documented in ONLINE_FORECAST.md.
2. **Mean:** `mean = sum of the last m CPU readings / m`, using up to 20 spaced active readings, with at least ten before forecasting begins. For readings 40%, 50%, 60%, the average is 50%. This is smoothing, not a claim that CPU cannot spike.
3. **Choice:** compare the absolute errors of the two forecasts over the last 20 eligible earlier matched targets. After ten verified comparisons, use the lower-error candidate. Before then, use trend. Equal errors keep trend. Store each candidate when issued, rather than recomputing past predictions after their outcomes are known.

Ten regularly spaced readings span 45 minutes; 20 span 95 minutes. The error-memory window counts matched forecasts, not guaranteed consecutive five-minute wall-clock readings. Future targets and targets exactly at the new issue time are excluded from learning. Completed migrations invalidate pending host outcomes as before. The planning-history guard prevents applying the original VM group's old history to a temporary subset.

## What the measurements show

Four real groups from each of 9 March, 22 March, 9 April and 20 April give 16 simulator cases. The final mean-selector design used the first three days as reused development checks. **20 April was unused for that final design before its first evaluation**, giving four new real cases. No parameter was changed after inspecting those outputs.

On common inputs/outcomes from the selector's observed path:

| Model | Pooled MAE, CPU percentage points |
|---|---:|
| Hold the current reading unchanged | 3.919 |
| Original time-based OLS | 3.796 |
| Half-trend | 3.666 |
| Previous adaptive trend | 3.625 |
| Trend and mean selector | 3.321 |

Across 18,685 matched predictions, selector error is about 12.5% lower than OLS and 8.4% lower than adaptive trend. It beats persistence, OLS and half-trend in all 16 real cases, and adaptive trend in 15/16. The exception is 9 April heavy: 5.946 pp versus adaptive 5.914. These are descriptive results on the tested cases, not independent population samples or proof of general superiority.

| Final-design new case, 20 April | Adaptive trend | Selector |
|---|---:|---:|
| real_a | 4.167 | 3.854 |
| real_b | 4.061 | 3.662 |
| real_c | 4.699 | 4.400 |
| heavy | 5.606 | 5.322 |

The repeated synthetic day/night case is another exception against adaptive trend: selector 3.749 versus 3.725 pp. It still improves OLS 4.012 and half-trend 3.782 on the same simulator outcomes. Do not count repetitions of that synthetic case as independent workloads.

Complete fixed-placement sequences were checked separately, without migration or cloudlet completion. On unused 20 April, selector MAE is 3.691 versus adaptive 4.193 pp across 259 fixed host groups. On the separate top-16 load-selected stress group it is 6.583 versus 7.464. Mean alone is better in some population groups, but fails the synthetic trend case; the selector is a balance, not the best specialist everywhere.

Early warning also has a trade-off: among 72 new >80% crossings in that fixed heavy set, selector warns before 45, with 27 false warnings; adaptive trend warns before 17, with 14 false warnings. Thus more events are caught, but more unnecessary warnings occur. These are fixed-sequence decisions, not completed migrations or a demonstrated energy benefit.

## Simulator checks and energy boundary

Independent Python replay checks both Java trend weights and selector decisions, including every mean input's timing and spacing. Java regression tests cover future/same-time exclusion, warmup, bounded trend weight, rolling memory and adaptation when old errors expire. A repeated complete selector CSV is byte-identical.

Original-day checks also completed. Synthetic energy remains 5.244283 kWh with two moves, near THR but above IQR. Original heavy still matches THR at 8.253002 kWh/four moves and trails IQR at 7.040498/zero. No energy win over both baselines is established.

Actual metric snapshots use allocated host CPU, not every demand state. Some history readings are a sampling interval old at a decision; the forecast target still uses current simulation time plus 300 seconds. Generation exclusions and matching are conditional observations, not a counterfactual no-migration trace.

## Full-day workload check, kept separate

The legacy jobs have a finite 20-million-MI-per-core length; heavy jobs can finish well before all 24 hours of input trace are exercised. A separate FullDay switch uses one billion MI per core, keeping jobs active throughout the requested day. Every policy receives the same change. Actual simulation end times remain recorded in CSV; shutdown/event tails can extend beyond the requested horizon.

The initial length -1 attempt generated 3,141,832 host-history entries, then exposed quadratic indexed LinkedList traversal in result calculation. The run was stopped and its thread/history evidence retained. Result calculation now snapshots the list into an ArrayList, preserving entry order and arithmetic. Long finite jobs avoid the tiny-update behavior. This is a performance fix, not an energy-model change.

On full-day 20 April heavy, selector and THR both use 20.804640 kWh/two moves; IQR uses 21.122948/one. The selector has zero predictive overload flags there, so its apparent advantage over IQR is shared with THR and must not be credited to forecasting. Full-day heavy point MAE improves from adaptive 7.142 to selector 6.353 pp. Synthetic selector uses 9.920121 versus THR 9.922664 and IQR 9.904820 kWh; this does not establish overall superiority either.

## Simpler active code and reproduction

The recent-pair regression and its pair-based selector were screened, then removed from active Java after rolling-average controls matched or beat them. Their complete source/results remain in Git checkpoint 7e36dd6. The VM-level aggregation experiment gave only a small/mixed gain and was not added to Java. Five production source files remain.

Current explicit research command:

`./scripts/research/run_forecast_validation.ps1 -Day 20110420 -Model mean_guard -Label another_check -StablePlanning`

Add `-FullDay` for the separate long-running-job benchmark. Existing labels cannot be overwritten. The old default OLS reproduction remains unchanged. New CSV rows retain historical policy label LR; run.json identifies the actual research model/options. Earlier preparation provenance's candidate field describes the reference candidate at preparation, not every later model; per-run options and study plans are authoritative.

All ten days in the pinned author PlanetLab archive have now been inspected somewhere in this investigation. They cannot be described as untouched holdouts for future designs. Preserve the staged first-use validation history; later tests on these days are reuse checks. Further prospective validation needs genuinely reserved/new data or a clearly stated exploratory evaluation design.
