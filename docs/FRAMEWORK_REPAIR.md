# Framework repair and fresh-day validation

27 November: a separate underload path mutates physical VM lists during planning. The opt-in numeric reservation repair and its resource/exception/integration checks are documented in [UNDERLOAD_PLANNING_AUDIT.md](UNDERLOAD_PLANNING_AUDIT.md). It is separate from the default mutable-map repair below; enabling only FrameworkFix does not replace underload planning.

**Later correction:** see POWER_ACCOUNTING_ERRATUM.md before using historical energy examples. The current run helper enables this repair/stable planning by default after power-corrected paths exposed the old defect. Earlier opt-in descriptions below record that stage of the investigation.

## Repair

CloudSim Plus 8.0.0 returns Collections.emptyMap() in getMigrationMapFromOverloadedHosts when no host is overloaded. Its underloaded-host phase may then putAll into that immutable map. This was verified against the locally cached official 8.0.0 source jar and the original runtime class.

The opt-in FrameworkFix build step changes exactly one invocation in that private method into construction of a mutable HashMap. It writes a generated class into the run-specific classpath; the original dependency jar is never edited. The patch tool fails unless exactly one expected site is found. A regression test confirmed the original returned map rejects writes and the repaired map accepts them. The runtime class is cleared before every helper run and again in finally, including failed runs. A subsequent unpatched regression test again confirmed the original immutable behavior, demonstrating isolation.

No underload-threshold workaround is used in repaired runs. All three policies receive the same repair and ordinary underload consolidation is restored. Two original-day repaired full batches completed with 15 rows each and matching displayed values. The default dependency remains 8.0.0, and the repair is not silently enabled for historical reproduction.

## Fresh-day test

The candidate remains ten spaced observations plus half the fitted trend, selected before this test. Downloaded the original author's PlanetLab repository and used 6 March 2011, rather than the 3 March data used earlier. The repository commit, download digest, selected file digests and fixed-list procedure are recorded in the holdout provenance file. There are 898 source traces that day; 76 were retained for four disjoint real cases. The 16 heaviest traces define a retrospective stress case; 60 evenly spaced names from the remaining sorted pool define three groups. Selection uses workload characteristics, not model outcomes. The synthetic diurnal case is repeated only as a regression check and is not fresh validation.

| New real case | Matched forecasts | Persistence MAE (pp) | OLS MAE (pp) | Half-trend MAE (pp) |
|---|---:|---:|---:|---:|
| real_a | 1395 | 2.644 | 2.510 | 2.444 |
| real_b | 1400 | 3.946 | 3.470 | 3.515 |
| real_c | 1395 | 3.764 | 3.543 | 3.496 |
| heavy | 442 | 3.819 | 4.608 | 4.053 |

Half-trend beats original OLS in three of four fresh real cases. Original OLS is better on real_b; persistence is better on heavy. This is not a universal accuracy win. Evaluation is on matched allocated-CPU forecasts, excluding completed topology changes, not every workload moment; forecasts can originate in temporary source-evacuation checks too. Source inspection on 18 November corrected the earlier destination-probe description: destination placement uses a private framework threshold check.

In the new heavy case, THR uses 8.2876 kWh with 3 moves, IQR 7.0126 with 1 move, and half-trend LR 8.2963 with 3 moves. Improved prediction error therefore has not established energy superiority. The original synthetic case still has excess LR moves when normal underload consolidation is restored. Continue investigating migration decision utility and destination/source distinction, rather than tuning the report toward a winner.

## Reproduction

Use the normal run helper with FrameworkFix explicitly selected. Candidate properties are spacedHistory, dampedForecast and evaluateForecast; noUnderload is not enabled. For the fresh day, researchWorkloads points to results/predictor_investigation/holdout_20110306/manifests. Existing default five cases and report evidence are untouched. Accuracy analysis uses --locked --all-heldout for the fresh day; no retuning occurs. See scripts/analyze_forecast_accuracy.py.

Primary dataset source: https://github.com/beloglazov/planetlab-workload-traces. Framework source: official org.cloudsimplus:cloudsim-plus:8.0.0-sources.jar. ASM is pinned to 9.7.1 for the local compatibility tool. Two Java tools under scripts/framework are build/regression tools, not extra active project classes.

PR2, its template boundary and results/experiments.csv remain unchanged. No published result has been replaced with new research results.
