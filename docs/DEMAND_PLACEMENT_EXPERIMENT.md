# Retired destination-demand experiment

Recorded 18 November 2026. This was a separately labeled environment test, not a forecasting improvement or a change to frozen PR2.

The prototype replaced one private post-placement CPU-threshold calculation with current cloudlet demand, while retaining all physical CPU/RAM/bandwidth/storage admission checks. The candidate and every baseline used the same rule. A test confirmed that a physically fitting fourth idle VM was accepted and a fifth was still rejected by physical CPU capacity; clearing the runtime override restored the old fourth-VM threshold rejection.

Before the later power correction, all three full-day comparison batches completed but did not show a predictive energy gain. The subsequent power audit found that those energy numbers were not physically validated and they must not be used to credit this prototype.

After applying the migration power guard, the first exploratory batch completed 13 case/policy runs, then spent excessive time in heavy IQR's growing-history threshold calculation. A thread sample and history histogram were retained. It was stopped without a complete CSV. The forecast-disabled comparison batch was not run. There is no completed corrected-environment energy comparison for this prototype.

The demand-placement helper and CLI/runtime patch option were removed from active code. Its intermediate source is retained as scripts/research/attempts/demand_placement_with_power_guard.patch against c6ac073, including the power correction being developed concurrently. The immutable plan and partial log/thread/history evidence remain under results/predictor_investigation. The archive is a failed/incomplete experiment, not an approved configuration.

Current default placement semantics remain unchanged. Future admission/placement research must independently address feasibility, iteration stability and equal-policy controls before producing energy conclusions.
