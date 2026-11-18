# Energy accounting erratum: 18 November 2026

**Historical energy comparisons from the instant-shutdown configuration must be revalidated before being used as evidence of energy savings.** The stored PR2/report CSV is preserved as historical output, not silently replaced. Quiet cases without migration were unaffected in the reproduced audit; migrating cases require corrected runs.

## Plain explanation

A server must stay switched on while a VM is arriving. In the old configuration, an empty destination could switch off during migration because its VM list was still empty. The arriving VM could later receive CPU while that server remained marked off. Our energy calculation correctly followed the recorded power flag, but that flag no longer matched the simulated CPU work, so energy was omitted.

This is a simulator lifecycle problem, not proof that a forecasting algorithm saved energy. A lower energy number with this inconsistency is not a physically valid improvement.

## Evidence

The legacy configuration was reproduced with the new diagnostic only; no historical CSV was modified. The audit counted active-demand VMs receiving allocated MIPS on hosts marked inactive. It separately checked every stored host-history entry for positive allocated CPU while inactive.

| Reproduced legacy case | Policy | Off-host VM sample count | Inactive history entries with CPU |
|---|---|---:|---:|
| diurnal | THR | 57 | 101 |
| diurnal | IQR | 72 | 110 |
| diurnal | original LR | 29 | 46 |
| heavy | THR | 93 | 136 |
| heavy | IQR | 0 | 0 |
| heavy | original LR | 7 | 9 |

All nine quiet-case policy rows had zero violations in that reproduction. The exact old configuration can vary in planning order; this audit does not pretend to reconstruct every historical run's internal states from its CSV alone.

Official implementation source inspected: [CloudSim Plus 8.0.0 source archive](https://repo.maven.apache.org/maven2/org/cloudsimplus/cloudsim-plus/8.0.0/cloudsim-plus-8.0.0-sources.jar). HostSimple.addMigratingInVm reserves an incoming VM and calls updateProcessing before it enters the normal VM list. The update's empty-list shutdown test does not account for incoming VMs. VM processing is not blocked solely because the power flag is false.

## Narrow correction

The common harness now uses a small nested MigrationAwareHost, in the existing simulation Java file. During a processing update with an incoming VM, it temporarily disables the idle deadline, then restores it in finally. Hosts with no resident or incoming VMs still shut down normally. No extra production source file or changed power curve is needed.

A standalone regression reproduces the unguarded shutdown, verifies protection during transfer and verifies genuine empty shutdown afterward. The guard is now enabled by default. Legacy reproduction requires explicit `coe619.powerGuard=false`, or an old Git checkpoint. Do not use that legacy mode for new physical energy claims.

The five-minute idle-delay experiment also removed sampled violations, but the direct incoming-VM guard avoids imposing an arbitrary five-minute powered-idle period. Default shutdown deadline can remain zero.

Correcting power exposed the previously identified CloudSim immutable-map failure in a new default path. The run helper therefore enables the verified compatibility map repair and stable planning by default. The original dependency jar remains unchanged. VS Code launch tasks prepare the generated runtime class before debugging and clear it afterward; ordinary helper runs clear it in finally. A subsequent helper also clears a stale override before any build.

## Corrected original-job benchmark

These rows use the current five fixed cases, finite legacy job lengths and corrected power behavior. THR/IQR and all resources are shared. Original LR is retained as a reference; the selector also includes the separately documented planning-history guard.

| Case | THR kWh / moves | IQR kWh / moves | Original LR kWh / moves | Trend/mean candidate kWh / moves |
|---|---|---|---|---|
| diurnal | 5.827265 / 2 | 5.943861 / 2 | 6.278867 / 6 | 5.823233 / 2 |
| heavy | 9.615548 / 4 | 7.040498 / 0 | 9.641305 / 6 | 9.615548 / 4 |

The tiny synthetic difference versus THR is not a broad predictive-energy claim. IQR remains clearly better on original heavy. The candidate removes extra moves compared with the original predictor, but history/planning fixes and forecast-model effects must remain distinguished.

Corrected default, corrected candidate and both final full-day batches each pass all 15 VM-sample and 15 history-entry audits with zero violations. These are recorded-state checks, not a proof about every unrecorded instant or all future configurations.

A subsequent bounded-forecast batch adds15 verified policy runs with an unchanged result CSV: the reproducible audit summary now covers75 runs and150 zero-violation checks.

The historical model/data changes already tested are documented in TREND_MEAN_FORECAST.md. Its pre-correction energy examples are superseded by this erratum. The forecast-only fixed-sequence experiments do not use host power flags. The same frozen selector was additionally replay-verified under the corrected power environment; forecasting improved in all four real full-day groups versus adaptive trend, while energy superiority over both baselines remains unproved.

## Where the evidence is

- results/predictor_investigation/legacy_default_power_audit
- results/predictor_investigation/corrected_default_power_audit
- results/predictor_investigation/corrected_mean_power_audit
- results/predictor_investigation/online_holdout_20110420/validated_power_mean and validated_power_control
- scripts/research/MigrationPowerRegression.java

PR2 PDF, report CSV and prior meeting files remain frozen records. Their energy interpretations need this qualification and later revision when the user provides the report template. Do not present their old rankings as the current validated outcome.
