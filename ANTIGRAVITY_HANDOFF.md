# Antigravity Handoff — Baseline Simulation Fixes

## Resume point

The THR and IQR baseline simulations now compile and finish with all submitted VMs and cloudlets accounted for. Resume with Progress Report I (PR1) in LaTeX, using placeholders for figures that Zahed will add. Do not treat the single-run metrics below as statistically conclusive. Predictive-model development and presentation edits are still out of scope.

Restoration-fix commit: `42ebb323458af14b6edbfa6c4e91ea8d10e4ca5b`.
CPU-measurement correction commit: `661bf94c0b89a3c110cf9cc43dc895341821a036`.
Console-cleanup commit: `67f812cdb0fc7621c201cb71ee068efe5afc6203` (energy-window line removed; CloudSim log level WARN -> ERROR; presentation only).

## Latest evidence (user-run, after 67f812c) — use these for PR1

| Baseline | VMs | Cloudlets | Migrations | Energy | SLATAH | PDM | SLAV | ESV |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| THR | 8/8 | 8/8 | 2 | 35,311,961.05 J (9.8089 kWh) | 0 | 8.192e-06 | 0 | 0 |
| IQR | 8/8 | 8/8 | 2 | 35,311,761.57 J (9.8088 kWh) | 0 | 8.192e-06 | 0 | 0 |

Earlier verified THR (9.8085 kWh) differs slightly; cause unresolved, recorded as a reproducibility limitation.

**PR1 draft:** `docs/progress_report_1/PR1_Progress_Report_I.tex` (copy in `Course_Documents/04_Progress_Report_I/`). Two screenshot placeholders remain (THR, IQR output).

**PDM PE-factor fix (applied):** the PDM denominator now uses `Cloudlet.getTotalLength()` (MI per PE x PEs = 4e7 MI) to match the numerator's two-PE VM capacity. PDM halves from 8.192e-06 to 4.096e-06; SLAV/ESV unchanged (SLATAH = 0).

Post-fix verified runs (`mvn -q compile` + `exec:java`, JAVA_HOME = JDK 25; Maven's default JDK is too old to run the classes):

| Baseline | VMs | Cloudlets | Migrations | Energy | SLATAH | PDM | SLAV | ESV |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| THR | 8/8 | 8/8 | 2 | 35,312,123.97 J (9.8089 kWh) | 0 | 4.096e-06 | 0 | 0 |
| IQR | 8/8 | 8/8 | 2 | 35,311,543.37 J (9.8088 kWh) | 0 | 4.096e-06 | 0 | 0 |

PR1 uses these post-fix values. Small run-to-run energy variation remains unexplained.

Full continuation instructions: `docs/progress_report_1/ANTIGRAVITY_Resume_PR1.md`.

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
- Corrected host CPU history normalization. CloudSim Plus `HostStateHistoryEntry.percentUsage()` is allocated MIPS / requested MIPS, not utilization as a fraction of physical host capacity. Dynamic IQR, SLATAH, and the host power model now use allocated MIPS / total host MIPS capacity.
- Calibrated the IQR safety factor to 0.75: for normally distributed data, IQR is approximately twice MAD, so 0.75 IQR corresponds to the framework's commonly used 1.5 MAD factor. With capacity-normalized samples in [0, 1], IQR is at most 1 and the formula `1 - 0.75 * IQR` has a lower bound of 0.25 without clamping.
- Kept insufficient-history handling explicit: fewer than two active history samples raises `IllegalStateException`; CloudSim's dynamic policy treats an uncomputable threshold as `Double.MAX_VALUE` and delegates overload checks to the configured 0.80 static fallback.
- Added the observed host-history time envelope to output so the energy measurement interval is visible.

## Verified results

Environment: Maven 3.9.6, JDK 25.0.2, CloudSim Plus 8.0.0. `mvn -q compile` was run immediately before verification. THR was run twice and produced identical output; IQR was run once. These runs use the synthetic diurnal traces.

| Baseline | VMs | Cloudlets | Successful migrations | Energy | History envelope (s) | SLATAH | PDM | SLAV |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| THR | 8/8 | 8/8 | 2 | 35,310,734.41 J (9.8085 kWh) | 300.1–46,500.6 | 0 | 8.192000000e-06 | 0 |
| IQR | 8/8 | 8/8 | 2 | 35,311,543.37 J (9.8088 kWh) | 300.1–46,500.1 | 0 | 8.192000000e-06 | 0 |

Both baselines exited successfully, showed successful completion for every cloudlet, and produced no VM restoration or host RAM/bandwidth allocation errors. The IQR overload warnings in the verified run used positive thresholds (0.72 and 0.73); no host at zero utilization was flagged as overloaded. Zero SLATAH/SLAV values are calculated outcomes, not forced values. SLATAH counts intervals at physical host CPU utilization of at least 99.9%; it is distinct from the 80% THR policy threshold.

The prior handoff's THR result (10.0909 kWh, SLATAH 0.001623) is superseded. It used `percentUsage()` as physical CPU utilization for energy and SLATAH, which uses the wrong denominator. The user's 10.0719 kWh/zero-SLATAH output is not reproducible from the corrected source with the Maven command below. The available evidence cannot establish whether VS Code launched stale classes or a different build/configuration. A fresh Maven compile followed by two identical THR runs produced 9.8085 kWh and SLATAH 0 both times. Compare the exact workspace and launch configuration if VS Code still differs.

The 24-hour `terminateAt(86_400)` is a simulation upper bound, not the energy integration duration. Cloudlets finish at approximately 46,500 seconds (12.92 hours), and the last recorded host-history samples are at 46,500 seconds. Energy is integrated only between recorded history samples whose previous host state is active; the observed history envelope begins at 300.1 seconds and ends at about 46,500 seconds. Idle intervals represented within that history are charged at the host power model's idle/static power. No energy is extrapolated for the unrecorded idle tail from the last history sample to the 24-hour termination, nor for the initial interval before the first sample. Thus the reported values are sampled-workload-window energy, not full-day energy.

PDM applies the project's empirical 10% migration penalty to the **observed migration duration**, then normalizes by each VM's completed total cloudlet work (MI per PE x PEs) and averages over active VMs. This is a derived metric, not direct measurement of lost CPU instructions or a claim that CloudSim Plus explicitly models pre-copy behavior.

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
- Energy figures do not include the unrecorded idle tail through the 24-hour termination; report them as sampled-window results unless full-day idle integration is implemented and verified.
- IQR threshold behavior and SLA metric definitions should be explained and supported with primary references in PR1. In particular, SLAV is the chosen `SLATAH × PDM` composite, not asserted here to be a universal standard.
- The migration-derived PDM uses the project's empirical penalty assumption described above; interpret it accordingly.
- The policy can legitimately cancel a consolidation attempt when no destination host meets its constraints. Such warnings are not VM restoration failures.
