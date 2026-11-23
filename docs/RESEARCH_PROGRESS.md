# Research milestones recorded on 18 November 2026

**Current priority correction:** POWER_ACCOUNTING_ERRATUM.md explains a confirmed off-host-CPU/energy omission. Current default and corrected candidate pass recorded power audits; old energy comparisons are historical, not current validated savings.

These are actual completed checkpoints. Work completed early can be reported transparently in Term 262 after its calendar is supplied. Keep actual experiment/commit dates. PR2 and its completed Term 261 evidence remain frozen.

| Milestone | Result | Evidence / Git checkpoint |
|---|---|---|
| Half-trend and simulator repair | Normal consolidation restored; frozen half-trend tested on a different day | FRAMEWORK_REPAIR.md; abc5b1a |
| Adaptive replay | Learned how much trend to trust from earlier matched errors | ONLINE_FORECAST.md; be04ea1 |
| Integration and two new days | Beats persistence in 8/8 real cases, OLS in 6/8, half-trend in 7/8; gain over half-trend is modest | ONLINE_FORECAST.md; 03efe8f |
| Stable planning | Ordered source hosts/allocation maps; repeated CSV identical; no-forecast heavy controls match THR | PLANNING_REPRODUCIBILITY.md; df765a4 |
| History guard | Do not reuse the original VM group's trend after temporary removal; synthetic migrations fall from six to two | PLANNING_REPRODUCIBILITY.md; ab718f7 |
| Unsuccessful early-shutdown rule | Two full CSVs unchanged; archived the attempt and removed its runtime option | CONSOLIDATION_LIMITS.md; stable_online_energy results and archived patch |
| Admission limit verified | Four idle VMs reserve full host CPU capacity; fifth rejected despite spare RAM/BW | CapacityAdmissionCheck.java; verification/capacity_admission.txt |
| Complete-sequence screening | Compared all eligible targets, recent-pair regressions, VM aggregation and stronger mean/median/smoothing baselines; retained negative results | COMPLETE_FORECAST_STUDY.md; 7e36dd6 |
| Simpler trend/mean selector | Beats OLS/persistence/half-trend in 16/16 tested real cases, adaptive trend in 15/16; final design's new validation is four 20-April groups | TREND_MEAN_FORECAST.md; mean_selector_summary.json |
| Full-day and metric traversal | Longer jobs keep traces active; repaired quadratic linked-list traversal with unchanged stable CSV arithmetic | TREND_MEAN_FORECAST.md; full_day_initial_attempt and full_day_* runs |
| Power-state correction | Incoming migrations no longer permit idle shutdown; default compatibility/stable repair enabled; 60 corrected runs pass 120 recorded power checks | POWER_ACCOUNTING_ERRATUM.md; power_audit_summary.json |
| Retired placement prototype | Physical-admission unit tests passed; corrected heavy IQR run did not finish; no energy conclusion and no active placement option retained | DEMAND_PLACEMENT_EXPERIMENT.md; archived patch/partial evidence |
| Independent-provider validation | Frozen selector tested on Bitbrains; unchanged CPU remains competitive; a three-choice prototype has mixed disjoint-VM results and remains offline | INDEPENDENT_FORECAST_VALIDATION.md; bitbrains_holdout and bitbrains_reserved_vms |
| Physical output bounds | Allocated-CPU forecast limited to0–100%; actual15-row policy CSV unchanged and power audits pass | bounded_power_mean; current75 runs/150 power checks |
| Error-margin study | More early warnings but many additional false warnings; not added to migration control | forecast_margins |
| Further small-model checks | Persistence blending and two-reading regression show trade-offs/poorer results; retained offline, no extra Java model | FORECAST_FOLLOWUP_CHECKS.md |

The root COE619_Full_Context_v2.md retains complete migration history and an appended execution-log continuation. This versioned index records new milestones without duplicating historical administrative material.

## Current boundaries

- Five production Java source files remain under src/main/java. Standalone research checks are outside that tree.
- Changes remain opt-in research options. Default reproduction is available.
- The adaptive model uses ten spaced CPU readings and learns from each host's last 20 eligible verified errors. Until ten earlier errors exist, it uses half-trend. These are experimental design choices, not universal standards or novelty claims.
- Accuracy and energy are separate conclusions. Original heavy still favors IQR. Guarded synthetic is near THR but slightly above IQR.
- Scientific logs and unsuccessful experiments are evidence, not disposable staging files.
- Term 262 report partitioning awaits its calendar. Identify early work honestly. Explanations should remain ordinary English with examples and nearby attribution for research-specific definitions.

## Next bounded investigation

Current model/evidence: TREND_MEAN_FORECAST.md. All ten days of the pinned author archive have now been inspected; no remaining day there is an untouched holdout for another design. Preserve staged first-use history. The recent-pair runtime alternatives were retired after stronger simple controls; their source is retained at 7e36dd6.

Measure forecast-warning usefulness and feasible migration opportunities. Keep completed moves separate from temporary planning checks. Resolve admission assumptions before claiming that a different predictor alone can unlock more consolidation. Any new environment must apply equally to all baselines and retain the existing evidence.

## 20 November 2026 - Complete forecast accounting
The corrected-power full-day 20 April batch issued 6,341 forecasts: 6,293 were scored, two were excluded after completed migrations changed host membership, and 46 remained at simulation end. Every forecast has an explicit outcome; no unexplained missing observations or duplicate rounded target keys remain. All scored forecasts used a current latest observation, within floating-point tolerance. This is conditional evaluation coverage, not proof about every possible host moment.
Independent replay passed all 6,341 causal-weight checks. The complete experiments.csv is byte-identical to bounded_power_mean, and all 30 power audits are zero. The change labels exclusions and closes pending evaluation records at shutdown; it does not improve the predictor or establish an energy benefit. Existing cross-provider limitations and IQR's heavy-case advantage remain.
Evidence: results/predictor_investigation/closed_history_age_audit.json and online_holdout_20110420/closed_forecast_ledger. PR2 coordinator-form deliverables were separately prepared from the original template and renamed PR2_202045160 to match PR1. Original report evidence remains unchanged.

## 20 November 2026 - Conservative forecast blend rejected
An absolute-error-optimal blend of persistence and the bounded trend/mean candidate was tested on four already inspected input sets. It improved MAE in the first Bitbrains group but not in the second versus persistence, and degraded the existing PlanetLab candidate. It remains offline. All settings, source and input hashes, per-host scores and aggregate MAE/RMSE retained under absolute_blend_development. No further reserved VMs consumed. Independent-provider tail errors and typical error remain separate objectives; no energy or forecasting superiority claim added. See FORECAST_FOLLOWUP_CHECKS.md for the numeric comparison.

## 23 November 2026 - Robust forecast integration
Frozen robust/persistence selection beats persistence MAE and RMSE on two fresh disjoint Bitbrains cohorts. Opt-in Java integration passes6340 independent forecast checks and30 power checks; scored ledger fully accounted. Four real simulator cases improve MAE versus prior selector, synthetic worsens, energy matches THR. Existing mean-selector15-row control is byte-identical and6341 independent checks pass. No energy superiority claim. Evidence and reproduction: FORECAST_FOLLOWUP_CHECKS.md.

For later Term262 reporting, keep three distinct milestones: simulator/accounting reliability, robust forecast design and disjoint-VM validation, then end-to-end energy/performance evaluation (still unresolved). Work completed now remains dated now and can be identified as early preparation. No experiment or Git date is moved to next semester; calendar-based report assignment remains pending.


## 23 November 2026 - Energy evaluation clarified
Audited reactive decisions and tested one-interval forecast deferral against a no-forecast delay. Both reduce the heavy case from two moves to one but do not improve energy; removed those experimental policy options and retained a reproducible patch againstd976ceb. Offline trend-switching helps synthetic accuracy but worsens real comparisons, so it also stays offline.

Added a no-migration reference: it wins the tightly packed permanent-VM full-day benchmark. Added a separate equal-policy short/long-job lifecycle benchmark with VM release and a common24-hour energy/demand window. Consolidation saves40.41% on heavy versus no migration, but THR and robust LR match and sampled unmet demand is about0.008% versus zero. No predictive superiority claim. Original five-case results are byte-identical after feature isolation checks. See FORECAST_FOLLOWUP_CHECKS.md for exact numbers and qualifiers. Remaining738 Bitbrains VM files untouched.

## 23 November continuation - Shutdown and replay reliability
A source-only scale-in experiment separated source underload from destination exclusion and tested forecast CPU against current CPU. Both worsened energy versus the existing policy; retired the rule and archived its patch/test. Failed shutdown-drain attempts are explicitly incomplete, not energy results. MixedJobs now uses a synchronous exact-day observation rather than draining future events. Seven completed batches pass210 recorded power checks; original benchmark and cleaned baseline CSVs repeat exactly. Round-trip diagnostics permit exact replay of17,774 issued forecasts, but only10,711 have matched active targets; all other exclusions are accounted. No new predictor or predictive energy win. See FORECAST_FOLLOWUP_CHECKS.md for limitations and the next coverage audit.
