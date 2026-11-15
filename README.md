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

## Run from this repository in PowerShell

Use `./scripts/run.ps1` for the five-seed batch, or `./scripts/run.ps1 -Scenario diurnal -Seeds 1` for a short example. Demo runs write to results/demo and preserve the report evidence. Use -OutputDir results only when intentionally replacing the report batch. Use `./scripts/run.ps1 -Stats` to inspect trace statistics. JDK 25 is used locally; Maven targets Java 17 bytecode. CloudSim Plus is pinned to 8.0.0.

The heavy folder contains exactly 16 traces; stressed seeds reorder that same set. Five seeds do not mean five independent workload days. Fixed seeds may not remove simulator tie-order variation. Detailed limitations are in the guide.
