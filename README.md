# COE619: Energy-Aware Predictive VM Consolidation

MSc HPCC project by Zahid Alabadllah (202045160), KFUPM. Advisor: Dr. Mohamed Faten Zhani.

We evaluate whether simple CPU forecasting improves energy/performance trade-offs. Predictive energy superiority has not been demonstrated. Later forecast-accuracy improvements are recorded separately. Reactive THR warns at 80%, not 100%.

**Energy correction, 18 November:** instant shutdown could mark a destination off while its arriving VM received CPU. The current harness prevents this. Historical PR2 energy comparisons need the [power-accounting erratum](docs/POWER_ACCOUNTING_ERRATUM.md); preserved CSVs are not silently updated.

## Start here

- [Project Understanding Guide](docs/concepts_guide/COE619_Project_Concepts_And_Methods_Guide.pdf): definitions, simple formulas, evidence and limitations.
- [Corrected PR2](docs/progress_report_2/PR2_Progress_Report_II.pdf)
- [Corrected baseline](results/predictor_investigation/corrected_default_power_audit/summary.md) and [corrected research candidate](results/predictor_investigation/corrected_mean_power_audit/summary.md).
- [Historical PR2 summary](results/summary.md) and [original CSV](results/experiments.csv), preserved with the energy qualification above.
- [Subsequent research progress](docs/RESEARCH_PROGRESS.md): dated milestones, improved forecasting, planning fixes and remaining energy limitations. PR2 and its evidence remain frozen.
- [Current research predictor](docs/TREND_MEAN_FORECAST.md): simple trend-versus-average selection, tested gains, exceptions and reproduction.
- [Independent validation](docs/INDEPENDENT_FORECAST_VALIDATION.md): separate-provider checks, limitations, preserved future VMs and unused uncertainty experiments.
- PR1/PR2 and older handoffs describe historical versions; current code and the research errata take precedence for behavior and energy interpretation.

## Code map

- `ConsolidationExperiment`: hosts, VMs, workload setup and metric calculation.
- `ExperimentRunner`: THR/IQR/LR scenarios and CSV output.
- `PredictiveLinearRegressionPolicy`: ten-reading OLS forecast with static fallback.
- `HostCpuUtilization`: allocated MIPS / physical capacity.
- `HostLocalResourceProvisioner`: per-host resource accounting workaround.
- `archive/pr1/`: the three older standalone programs, outside the active Maven source tree.

## Simple workflow

Run `./scripts/run.ps1` for all five fixed cases, or `./scripts/run.ps1 -Scenario diurnal` / `-Scenario heavy` / `-Scenario real_a` for one case. Each compares THR, IQR and LR. There is no seed setting or averaging across workloads.

Demo output is written to results/demo. Preserve frozen report evidence until an explicitly requested report revision. JDK 25 is used locally; Maven targets Java 17. The helper uses target/meeting-build and enables the compatibility repair/stable planning automatically. The migration-aware power guard is on by default.

## Fixed workload cases

- diurnal: 8 synthetic day/night VMs on 4 hosts.
- real_a, real_b, real_c: three disjoint groups, each 20 real-trace VMs on 10 hosts.
- heavy: the 16 curated high-demand VMs on 6 hosts.

Exact ordered trace paths are committed under src/main/resources/workloads. All policies use the same manifest per case. The real groups were frozen once from a shuffled list (Python Random(619)); runtime does not sample or shuffle anything. They are illustrative cases, not a representative statistical sample of all cloud workloads.

Each result row is one case/policy observation. Inspect all cases rather than pooling unlike scenarios. Fixed inputs do not eliminate all simulator iteration-order variability. Repeat runs when needed to establish a specific result's reliability. Previous seed-based evidence is preserved under results/archive/seed-workflow. Historical PR1 and handoffs are records, not current run instructions.

The unused active subset has been removed. The old top-level 20110303 samples remain as historical inputs. Current inputs are diurnal, real/20110303, heavy, plus workload manifests. The project focus is validating the existing simple predictor, not adding algorithm or infrastructure complexity.

## VS Code layout

The active Java package contains five core classes. Historical programs are under archive/pr1, not src. THR and IQR remain in the current common harness.

Use **Terminal > Run Build Task** (Ctrl+Shift+B) for the complete fixed-workload comparison. **Terminal > Run Task** also offers day/night and heavy tasks. These tasks use the verified PowerShell helper and preserve report results. **Run and Debug** offers the same current programs through the Java extension. Local JDK paths belong in personal VS Code settings, not shared launch files.

Generated build files and LaTeX intermediates are hidden in this checkout's Explorer. PDFs nest under their editable LaTeX sources. The archive remains available for historical reference.

## Academic documents and cleanup

Research reproduction is documented in [forecast accuracy](docs/ONLINE_FORECAST.md), [stable planning/history guard](docs/PLANNING_REPRODUCIBILITY.md) and [capacity limitations](docs/CONSOLIDATION_LIMITS.md). Research options are explicit and do not replace the five-case default workflow.

Open ../Course_Documents/README.md for current academic deliverables. After verified artifact work run `./scripts/maintain_documents.ps1 -Clean` to sync course copies and remove known temporary output. Preview with `-Clean -DryRun`. Generated simulation files rebuild as needed.
