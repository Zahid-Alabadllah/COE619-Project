# Stable planning investigation, 18 November 2026

Fixed input traces do not by themselves fix every simulator tie. A full batch and a standalone heavy run gave slightly different energy even when the predictor recorded zero predictive flags. Forecast-disabled controls confirmed that these small differences should not be credited to forecasting.

Inspection of the official CloudSim Plus 8.0.0 source identified unordered planning collections. Overloaded hosts are collected into a set; HostSimple's generated hash includes the simulation object as well as host ID. The saved VM allocation and migration plans use HashMap. Restoration order can therefore change VM order and minimum-migration-time ties. This is a framework-ordering explanation, distinct from forecast accuracy.

## Explicit research option

`scripts/run.ps1 -FrameworkFix -StablePlanning` retains the original dependency jar and adds the following to the existing generated runtime compatibility class:

- Sort overloaded source hosts by host ID before planning their migrations, using Host's existing Comparable implementation.
- Replace the four original HashMap constructions in that framework class with insertion-ordered LinkedHashMap, including the saved allocation used for restoration.
- Use LinkedHashMap for the already-repaired empty-map path too.

The patch fails unless all four original map sites and the one source-ordering entry are present. It is opt-in, applies equally to THR/IQR/LR and is cleared before and after helper runs. PR2, its CSV, default reproduction and earlier research results are not rewritten.

## Checks

Original, mutable-map-only and stable-map variants are checked with MigrationMapRegression, followed by another original-class check after cleanup. Regression checks inspect mutability and the concrete saved-allocation/map types. The original dependency digest is checked by the repair helper.

Two full stable online batches on the 9 March workload produced byte-identical experiment CSVs. In both new heavy cases, the zero-predictive-flag online policy and the forecast-disabled control match THR energy exactly to the saved CSV precision. This supports the ordering repair in these tests; it is not proof of determinism for every possible simulation.

The eight real-case accuracy comparisons are unchanged at displayed precision. The synthetic diurnal path changes slightly: stable online policy still makes six migrations, so prediction's energy objective remains unresolved.

## Corrected call-path explanation

The official source's getOverloadedHosts calls our isOverloaded override for initial source detection. getVmsToMigrateFromOverloadedHost calls it again after temporarily removing a VM, to decide whether another VM must be selected. Destination checks instead call the framework's private isHostOverloaded(host, usagePercent) method. Earlier notes incorrectly described these extra predictor calls as destination probes; those notes have been corrected without changing historical measurements.

This reveals another research question: an old host-level trend describes the original VM group, so applying it unchanged after temporary VM removal may select unnecessary additional VMs. Any guard for that must be tested separately from the forecasting-model improvement.

## Tested history guard

The opt-in `coe619.planningHistoryGuard` snapshots each host's VM group at the start of optimization. If the framework temporarily changes that group, the predictor uses current CPU only to finish selecting VMs. It does not fit the original group's history to a different group. The snapshot is cleared in finally after each planning pass. Initial source overload detection still uses the online forecast. This is a history-validity design choice, not a new prediction formula.

With stable planning, the synthetic day/night case falls from six migrations and 5.824043 kWh to two migrations and 5.244283 kWh, about 10% lower energy than the unguarded experimental policy. THR uses 5.248772 and IQR 5.230971 kWh, so this removes a regression but does not beat both baselines. Four changed-composition checks are skipped per complete batch. A repeated guarded full CSV is byte-identical. Repeating the same synthetic case in multiple days is not independent evidence.

On the original heavy case, guarded online and THR both use 8.253002 kWh with four migrations; IQR remains better at 7.040498 kWh with zero. Sampled unmet demand remains nonzero and equal to THR there, so this is not a demonstrated performance improvement. On both new heavy cases the guard leaves the no-predictive-flag outcomes unchanged. The eight real-case prediction comparisons also remain unchanged. Independent causal-weight checks pass after the guard.

Reproduce with `scripts/research/run_forecast_validation.ps1 -Day 20110309 -Model online_guard -Label new_guard_check -StablePlanning`. Results are separate from unguarded/control evidence. Defaults and PR2 remain unchanged.

Primary source inspected locally: the official [CloudSim Plus 8.0.0 source archive](https://repo.maven.apache.org/maven2/org/cloudsimplus/cloudsim-plus/8.0.0/cloudsim-plus-8.0.0-sources.jar), especially VmAllocationPolicyMigrationAbstract and HostSimple. This is an implementation audit, not a universal research convention.
