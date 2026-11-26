# Research milestones recorded on 18 November 2026

27 November continuation: [Underload planning and migration-cost audit](UNDERLOAD_PLANNING_AUDIT.md). Numeric planning passes controlled fault/control and repeated full comparisons; 13,298 exact forecast replays. Found and separately quantified omitted migration costs caused by reset VM clocks. Heavy predictive energy still equals THR and exceeds IQR. Default benchmark/PR2 frozen; actual work date retained.

27 November follow-up: two shared current-demand placement variants and a frozen host-forecast admission veto tested with matching controls. Five batches/63 policy runs; 3,860 usable checks in the broader variant but zero forecast vetoes and identical predictive/control CSV. Shared changes have mixed energy/migration tradeoffs. Retired runtime options and preserved patches, plans and negative results; no new model or reserved-data consumption.

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

## 23 November - Forecast scope and a concrete placement barrier
Removed4047 off/empty-host forecasts with no change to any common prediction, energy, migrations or completed work. Added post-run operational scoring from power/CPU records:10805 eligible observed targets,2885 missing active observations and37 outside the window. Recovered94 previously omitted operational targets; offline and runtime label rules remain distinct. No future reference feeds the learner. Three batches pass90 recorded power checks and scope/reference-boundary checks. Heavy late-phase four-VM packing is blocked by the three-VM destination reservation limit, with measured active time at that policy floor. Next isolate that shared placement assumption before another predictive energy claim. Evidence and caveats: FORECAST_FOLLOWUP_CHECKS.md.

## 23 November 2026: placement-state audit before predictive energy claims

Tested the shared current-demand destination rule again under the exact24-hour mixed-job observer. It still cannot be accepted. In the matched heavy run, THR/LR reported19.896341kWh versus baseline10.837014, but the resident audit exposed the same VM7 seven times across three hosts. Those energy numbers are invalid for policy comparison. The first exploratory run omitted FullDay and used20million-MI long jobs; it is explicitly unmatched, not baseline evidence.

A small independent fixture, `scripts/research/UnderloadPlanningCheck.java`, reproduces the underload planner adding the same VM to source and target lists before a real migration begins. CloudSim8.0.0's underload path calls `addVmToMigrationMap`, which temporarily creates the actual candidate on a target; unlike the overload path, the top-level underload call does not restore allocation. The destination helper also receives an excluded-host set that its target predicate does not use. The latter is a separate source-inspection finding, not yet a tested repair.

Wrapping underload planning with the framework's generic save/restore was unsafe: unfinished VMs disappeared and only6of12short jobs completed. Adding scheduler deallocation did not fix it. Both superficially low-energy runs are marked INVALID. No such repair, demand-placement option or extra model remains in active code. Full experimental patch is archived against26ebac8 in `scripts/research/attempts/demand_underload_restoration_20261123.patch`.

Retained only `-AuditPlacement` in the research runner. This observational end-window check rejects duplicate entries, non-migrating cross-host copies, host-pointer mismatches, physical CPU over-capacity, missing unfinished VMs and resident VMs marked uncreated. The final15-case check passes all six categories;30recorded power checks pass and every CSV field exactly matches `scope_live_capacity`. This is an end-state check, not proof that every intermediate event is valid.

Evidence: `results/predictor_investigation/placement_state_audit/`, plus explicitly labeled run folders under `online_holdout_20110420`. PR2 and originalCSV unchanged. No new reserved workload data consumed. The full public optimizer also reproduces duplicate physical residency with the existing mutable-map repair; the direct underload call reproduces it against the unmodified dependency. Next: repair speculative underload reservations without corrupting the real scheduler or losing work. Validate exception rollback and in-flight migration state before reopening destination/model experiments. Forecast accuracy and predictive energy value remain separate, unresolved claims.

The public optimizer reproduction is now confirmed. An additional15-case ordinary finite-job compatibility run exactly reproduces `corrected_default_power_audit`. Its first audit attempt correctly exposed a guard-design mistake: graceful shutdown deliberately removes unfinished VMs, so absence after that cleanup is not evidence of lost work. Missing-job checking is therefore limited to the synchronous exact-window mixed-job mode. The first failed audit log is retained as a guard false positive, not a failed scientific benchmark. Final finite run passes all applicable resident checks and30power checks; it does not establish intermediate placement correctness.

## 25 November 2026 PR2 submission review

Reviewed and corrected the exact coordinator-template PR2 at Zahid's request for the 26 November 2026, Week 15 deadline (10% weight). Preserved the three-page layout, chart grid, sections, headers, footers, logos and styles. Clarified the period after PR1 Week 10 through 15 November and filled the exact deadline. Clarified OLS, workloads, preliminary outcomes, metric approximations and the historical energy-accounting caveat. Green chart markers indicate completion verified in Week 15, not actual completion dates. Later predictor experiments remain excluded; no predictive energy-saving claim is made. Advisor signature remains blank.

Current Word/PDF files retain the PR2_202045160 names in Course_Documents/05_Progress_Report_II. Archived the 20 November form and updated the folder index. Structural checks, visual inspection of all three pages and final hashes are recorded in docs/progress_report_2/coordinator_template/submission_review.json. Native Word export and bundled Poppler were used after the packaged renderer failed because LibreOffice is unavailable. No code, experiment output or historical CSV changed. Blackboard submission remains for the student.

## 25 November 2026 PR2 chart enhancement

At Zahid's request, replaced the chart's task grouping with seven milestone groups covering proposal/advisor confirmation, literature/setup, baselines/PR1, model design/PR2, predictor refinement/PR3, experiments/demonstration, and final report/presentation. Retained the original 24-column grid and merged cells. Grey markers show planned T261 checkpoints. Green means completed by a documented checkpoint: the first three groups by PR1 W10, initial model/report prepared by W15. No exact completion dates or progress percentages were invented. T262 targets W4/W7, W10/W12-13 and W14/W15 appear in task labels; columns 16-24 are unused. Initial OLS preparation is explicitly separate from the unfinished predictive objective.

Kept the report at three pages and verified the template geometry, table grids, merges, media, scientific text and planned/complete markers. Native Word export and all-page image inspection completed. The generator now edits a disposable target copy, protecting the original template from AutoSave; the original reference hash is unchanged. Earlier review files are archived under Course_Documents/05_Progress_Report_II/Archive/2026-11-25_Pre_Chart_Revision. Stable PDF/DOCX names remain unchanged. Advisor signature is still blank. No code, data or scientific conclusions changed.

## 25 November 2026 PR2 weekly phase bars aligned with PR1

At the user's request, replaced isolated completed-by checkpoints with phase bars. Read PR1_202045160.docx and carried forward its completed W1-3, W3-6 and W6-10 windows unchanged. Added W11-15 as 100% of initial PR2 scope only; the final predictor and energy benefit remain unfinished. Future T262 W1-7, W8-13 and W14-15 labels match PR1. Kept the original 24-week columns and seven task groups, normalized internal sample timeline merges and merged completed phase bars for percentage labels. No daily work dates were invented. Scientific report text and the 15 November work cutoff are unchanged.

Updated the editable Word, PDF, chart source XML, reconstruction helper and generation script. Retained three pages; checked every final rendered page, branding and page numbers. Archived the previous checkpoint chart. PR1, the retained coordinator template and experiment CSV are unchanged. Current filenames remain PR2_202045160.docx/pdf. Later predictor work is still outside PR2.
