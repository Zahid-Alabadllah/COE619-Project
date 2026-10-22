# Antigravity Handoff — Baseline Simulation Fixes

## Resume point

The THR and IQR baseline simulations now compile and finish with all submitted VMs and cloudlets accounted for. Resume with Progress Report I (PR1) in LaTeX, using placeholders for figures that Zahed will add. Do not treat the single-run metrics below as statistically conclusive. Predictive-model development and presentation edits are still out of scope.

Implementation commit: to be filled after commit.

## Root cause

CloudSim Plus 8.0.0's default `ResourceProvisionerSimple` tracks a VM's RAM and bandwidth allocation through mutable resource counters stored on the VM. During migration planning/restoration, the same VM object can be temporarily provisioned on a candidate destination while it is still allocated on its source host. The shared VM counters then no longer represent each host's independent reservation. Repeated planning can leave host RAM/bandwidth reservations inconsistent, eventually causing `Couldn't restore Vm ...` errors and unfinished cloudlets.

Increasing host RAM and bandwidth only delayed this failure; fresh runs with 64 GB RAM still reproduced RAM exhaustion. Those inflated capacities have been removed.

## Changes

- Added `HostLocalResourceProvisioner`, which tracks reservations independently per host and VM identity. Both baselines use it for RAM and bandwidth so migration planning cannot corrupt another host's accounting.
- Restored matching capacities in both baselines: 16,384 MB host RAM and 10,000 host bandwidth units.
- Kept the one-day synthetic diurnal workload, 4-host/8-VM topology, placement workaround, and baseline-specific THR versus IQR policies.
- Added end-of-run checks: fail explicitly unless all 8 VMs were created and all 8 cloudlets finished successfully.
- Recorded migration duration from the VM migration-start and datacenter migration-finish event times. PDM, SLAV, and ESV use scientific notation so small computed values are not rounded to an apparent zero.
- Added the eight required synthetic diurnal trace files under `src/main/resources/planetlab/diurnal/`.

## Verified results

Environment: Maven 3.9.6, JDK 25.0.2, CloudSim Plus 8.0.0. Each simulation was run once against the synthetic diurnal traces.

| Baseline | VMs | Cloudlets | Successful migrations | Energy | SLATAH | PDM | SLAV |
|---|---:|---:|---:|---:|---:|---:|---:|
| THR | 8/8 | 8/8 | 2 | 36,327,382.28 J (10.0909 kWh) | 0.001623 | 8.192000000e-06 | 1.329857895e-08 |
| IQR | 8/8 | 8/8 | 4 | 36,990,461.13 J (10.2751 kWh) | 0 | 1.638400000e-05 | 0 |

Both runs exited successfully, showed successful completion for every cloudlet, and produced no VM restoration or host RAM/bandwidth allocation errors. The zero IQR SLATAH/SLAV values are calculated outcomes, not forced values. The THR SLATAH counts time at host CPU utilization of at least 99.9%; it is distinct from the 80% policy threshold.

PDM applies the project's empirical 10% migration penalty to the **observed migration duration**, then normalizes by each VM's completed cloudlet instruction length and averages over active VMs. This is a derived metric, not direct measurement of lost CPU instructions or a claim that CloudSim Plus explicitly models pre-copy behavior.

## Run commands

Run these from the repository root:

```powershell
mvn -q compile
mvn -q exec:java "-Dexec.mainClass=kfupm.coe619.BaselineReactiveSimulation"
mvn -q exec:java "-Dexec.mainClass=kfupm.coe619.BaselineIQRSimulation"
```

## Remaining limitations

- The workloads are synthetic diurnal traces, not the PlanetLab traces used by the existing `20110303` resource set.
- Only one run per baseline was verified; repeat runs and statistical comparison are still needed before making research claims.
- IQR threshold behavior and SLA metric definitions should be explained and supported with primary references in PR1. In particular, SLAV is the chosen `SLATAH × PDM` composite, not asserted here to be a universal standard.
- The migration-derived PDM uses the project's empirical penalty assumption described above; interpret it accordingly.
- The policy can legitimately cancel a consolidation attempt when no destination host meets its constraints. Such warnings are not VM restoration failures.
