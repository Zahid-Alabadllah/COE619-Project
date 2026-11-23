# Why better forecasts do not automatically save energy

**Later findings:** POWER_ACCOUNTING_ERRATUM.md corrects the instant-shutdown power race and supersedes historical energy examples. A separately attempted demand-placement prototype was retired after an incomplete/stalled IQR benchmark; see DEMAND_PLACEMENT_EXPERIMENT.md. Its code is not an active runner option.

Recorded 18 November 2026. These are implementation findings, not changes to PR2.

Our scheduler reserves each VM's full virtual CPU capacity when deciding whether it fits. CPU usage in a workload trace is a different quantity. A mostly idle VM still reserves its virtual CPUs.

The current host has eight 1,000-MIPS cores. Each VM reserves two 1,000-MIPS virtual cores. Four VMs therefore reserve the host's 8,000 MIPS. A fifth cannot be admitted, even if the existing VMs are idle and RAM/bandwidth are available.

scripts/research/CapacityAdmissionCheck.java verified this with the current scheduler and host-local provisioners: four idle VMs were created, the fifth failed CPU admission, and spare RAM/bandwidth remained. Output is retained in results/predictor_investigation/verification/capacity_admission.txt.

While all VMs remain resident, 20 VMs therefore need at least five hosts and 16 need at least four. This is not a bound on whole-run energy: VMs can finish and leave, and workloads/migrations affect the path. It limits additional packing in quiet cases that already start tightly packed.

The framework's underload check uses scheduler-requested MIPS shares; our forecast samples use allocated host CPU observations. Those are not interchangeable. Light trace usage does not automatically release reserved virtual CPU admission capacity.

The extended capacity check additionally verifies a stricter migration-placement limit: with three idle VMs, a fourth physically fits, but the 80% post-placement policy check rejects it because reserved CPU would be100%. Initial placement can still create four VMs. Thus a temporarily opened host is not necessarily easy to evacuate again even after actual CPU falls. This is a modeling choice in the framework, not a proven forecast-math bug. A demand-aware check would be a separately tested assumption, applied to every policy and still subject to physical/resource admission.

## Early-underload experiment

A separate experiment reused the online forecast to identify an upcoming low-load host. It also affected the framework's low-load destination filter. It produced many conditional flags but no change in either complete 15-row CSV versus the guarded policy on 9 and 22 March. Flags are not evidence of useful completed moves.

The unused rule was removed from active code. Its source diff against commit ab718f7 is retained as scripts/research/attempts/predictive_underload.patch, with options/logs in each day's stable_online_energy folder. To reproduce that historical experiment, use that base commit and patch; it is absent from current runner options.

## Next scientific decision

Keep the accuracy candidate and planning-history guard separate from benchmark changes. Investigate warning usefulness where feasible migrations exist. If CPU overcommit is introduced, define admission, fairness and performance behavior and rerun all policies under the same environment. That is a new benchmark assumption, not a correction to old data.

Current evidence supports a modest forecasting improvement and removal of a planning regression. Predictive energy superiority over IQR remains unproved.

Primary implementation source: the official [CloudSim Plus 8.0.0 source archive](https://repo.maven.apache.org/maven2/org/cloudsimplus/cloudsim-plus/8.0.0/cloudsim-plus-8.0.0-sources.jar), particularly VmSimple, VmSchedulerTimeShared, VmSchedulerAbstract and VmAllocationPolicyMigrationAbstract.

## 23 November - Quantified energy bottleneck
The corrected full-day heavy comparison makes the limitation concrete. THR/robust uses120.3333 active host-hours, IQR120.0833; a15-minute difference. At175W fixed active power this contributes0.04375kWh, close to their measured0.043875kWh gap. A forecast-based one-reading deferral reduced moves from two to one but left host-hours unchanged and slightly increased energy. A no-forecast delay produced the same heavy result, so the change was retired.

For identical hosts, the model sums to175W times the number of active hosts plus75W times summed normalized allocated CPU. When delivered CPU work is comparable, repacking VMs among already active hosts offers little fixed-power saving. The next energy investigation should target a feasible reduction in active-host duration, while treating capacity admission, cold-start behavior and performance constraints explicitly. Better forecast MAE or fewer migrations alone is insufficient.

## Separate lifecycle benchmark
A no-migration reference beats the predictive policy in the fixed full-day heavy and day/night cases because the VM population is permanent and starts tightly packed. That is not a suitable sole test of energy savings from consolidation. A separate opt-in mixedJobs benchmark lets short jobs finish and releases their VMs; all policies receive identical synthetic job lengths and the same real CPU traces. Common24-hour measurements show genuine consolidation savings versus no migration, including40.41% in the heavy case. THR and robust LR match, and small nonzero sampled unmet demand must be reported. This fixes an evaluation gap; it does not prove a predictive energy gain. Exact protocol, caveats and run locations are in FORECAST_FOLLOWUP_CHECKS.md.

## 23 November - Quantified late placement floor
Host/job exports confirm the heavy case's final21.1467hours contain four unfinished long VMs on two hosts. Physical nominal capacity permits four VMs on one host, but the fixed80% reserved-CPU migration check permits only three. Given the study's one-long-per-initial-host layout, the controller exactly reaches the two-host floor allowed by that check. The one-host versus two-host fixed-power component differs by3.7007kWh, but that is not an achievable-savings guarantee: true CPU headroom and migration constraints are omitted from the physical floor. This identifies a common placement assumption to test, rather than evidence that another forecaster alone will save that amount. See forecast_scope_audit/live_capacity/results.json and the official CloudSim8 source archive linked above.

## 23 November 2026: placement-state audit before predictive energy claims

Tested the shared current-demand destination rule again under the exact24-hour mixed-job observer. It still cannot be accepted. In the matched heavy run, THR/LR reported19.896341kWh versus baseline10.837014, but the resident audit exposed the same VM7 seven times across three hosts. Those energy numbers are invalid for policy comparison. The first exploratory run omitted FullDay and used20million-MI long jobs; it is explicitly unmatched, not baseline evidence.

A small independent fixture, `scripts/research/UnderloadPlanningCheck.java`, reproduces the underload planner adding the same VM to source and target lists before a real migration begins. CloudSim8.0.0's underload path calls `addVmToMigrationMap`, which temporarily creates the actual candidate on a target; unlike the overload path, the top-level underload call does not restore allocation. The destination helper also receives an excluded-host set that its target predicate does not use. The latter is a separate source-inspection finding, not yet a tested repair.

Wrapping underload planning with the framework's generic save/restore was unsafe: unfinished VMs disappeared and only6of12short jobs completed. Adding scheduler deallocation did not fix it. Both superficially low-energy runs are marked INVALID. No such repair, demand-placement option or extra model remains in active code. Full experimental patch is archived against26ebac8 in `scripts/research/attempts/demand_underload_restoration_20261123.patch`.

Retained only `-AuditPlacement` in the research runner. This observational end-window check rejects duplicate entries, non-migrating cross-host copies, host-pointer mismatches, physical CPU over-capacity, missing unfinished VMs and resident VMs marked uncreated. The final15-case check passes all six categories;30recorded power checks pass and every CSV field exactly matches `scope_live_capacity`. This is an end-state check, not proof that every intermediate event is valid.

Evidence: `results/predictor_investigation/placement_state_audit/`, plus explicitly labeled run folders under `online_holdout_20110420`. PR2 and originalCSV unchanged. No new reserved workload data consumed. The full public optimizer also reproduces duplicate physical residency with the existing mutable-map repair; the direct underload call reproduces it against the unmodified dependency. Next: repair speculative underload reservations without corrupting the real scheduler or losing work. Validate exception rollback and in-flight migration state before reopening destination/model experiments. Forecast accuracy and predictive energy value remain separate, unresolved claims.

The public optimizer reproduction is now confirmed. An additional15-case ordinary finite-job compatibility run exactly reproduces `corrected_default_power_audit`. Its first audit attempt correctly exposed a guard-design mistake: graceful shutdown deliberately removes unfinished VMs, so absence after that cleanup is not evidence of lost work. Missing-job checking is therefore limited to the synchronous exact-window mixed-job mode. The first failed audit log is retained as a guard false positive, not a failed scientific benchmark. Final finite run passes all applicable resident checks and30power checks; it does not establish intermediate placement correctness.
