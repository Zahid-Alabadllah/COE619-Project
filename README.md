# COE619: Energy-Aware Predictive VM Consolidation

MSc HPCC project by Zahid Alabadllah (202045160), KFUPM. Advisor: Dr. Mohamed Faten Zhani.

We evaluate whether simple CPU trend prediction improves energy/performance trade-offs. Predictive superiority has not been demonstrated. Reactive THR warns at 80%, not 100%.

## Start here

- [Project Understanding Guide](docs/concepts_guide/COE619_Project_Concepts_And_Methods_Guide.pdf): definitions, simple formulas, evidence and limitations.
- [Corrected PR2](docs/progress_report_2/PR2_Progress_Report_II.pdf)
- [Results](results/summary.md), generated from [raw runs](results/experiments.csv).
- PR1 and older handoffs describe historical versions; current code and corrected PR2 take precedence.

## Code map

- `ConsolidationExperiment`: hosts, VMs, workload setup and metric calculation.
- `ExperimentRunner`: THR/IQR/LR scenarios and CSV output.
- `PredictiveLinearRegressionPolicy`: ten-reading OLS forecast with static fallback.
- `HostCpuUtilization`: allocated MIPS / physical capacity.
- `HostLocalResourceProvisioner`: per-host resource accounting workaround.
- `TraceStats`: dataset statistics.
- `Baseline*Simulation`: historical baseline programs retained for PR1 reproducibility.

## Simple workflow

Run `./scripts/run.ps1` for all five fixed cases, or `./scripts/run.ps1 -Scenario diurnal` / `-Scenario heavy` / `-Scenario real_a` for one case. Each compares THR, IQR and LR. Use `./scripts/run.ps1 -Stats` for workload statistics. There is no seed setting or averaging across workloads.

Demo output is written to results/demo. Add `-OutputDir results` only when deliberately regenerating the report evidence. JDK 25 is used locally; Maven targets Java 17. The helper uses target/meeting-build to avoid IDE output collisions.

## Fixed workload cases

- diurnal: 8 synthetic day/night VMs on 4 hosts.
- real_a, real_b, real_c: three disjoint groups, each 20 real-trace VMs on 10 hosts.
- heavy: the 16 curated high-demand VMs on 6 hosts.

Exact ordered trace paths are committed under src/main/resources/workloads. All policies use the same manifest per case. The real groups were frozen once from a shuffled list (Python Random(619)); runtime does not sample or shuffle anything. They are illustrative cases, not a representative statistical sample of all cloud workloads.

Each result row is one case/policy observation. Inspect all cases rather than pooling unlike scenarios. Fixed inputs do not eliminate all simulator iteration-order variability. Repeat runs when needed to establish a specific result's reliability. Previous seed-based evidence is preserved under results/archive/seed-workflow. Historical PR1 and handoffs are records, not current run instructions.

The active and old top-level 20110303 subsets are unused by the current runner; they are retained for history. Current inputs are diurnal, real/20110303, heavy, plus workload manifests. The project focus is validating the existing simple predictor, not adding algorithm or infrastructure complexity.
