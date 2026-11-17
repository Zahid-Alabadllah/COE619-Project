# Why better forecasts do not automatically save energy

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
