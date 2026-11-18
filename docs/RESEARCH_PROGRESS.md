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
