package kfupm.coe619;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigration;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationAbstract;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationDynamicUpperThresholdFirstFit;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.hosts.HostStateHistoryEntry;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicyMinimumMigrationTime;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelPlanetLab;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Unified experiment harness: runs one consolidation policy (THR, IQR, or predictive LR)
 * on one workload scenario and returns its metrics.
 *
 * <p>Differences from the original baseline classes (kept unchanged for PR1 reproducibility):
 * <ul>
 *   <li>Idle hosts are shut down after {@link Config#idleShutdownSeconds}, so consolidation can
 *       actually save idle power. The original baselines never power hosts off.</li>
 *   <li>PDM denominator: total length of the VM's finished cloudlets; if none finished (full-day
 *       PlanetLab runs), VM MIPS capacity x VM execution time is used instead.</li>
 *   <li>Unmet demand: once per scheduling interval, each running VM's trace demand
 *       (trace utilization x VM MIPS) is compared with the CPU its host actually gave it; the
 *       shortfall is summed and reported as a fraction of total demand. This captures CPU
 *       throttling that SLATAH (time at 100% host allocation) can miss.</li>
 * </ul>
 *
 * <p>Run with {@code -Dcoe619.trace=true} to print migrations and host power events.</p>
 */
public final class ConsolidationExperiment {

    public enum Policy { THR, IQR, LR }

    /** Do not power down an empty destination while a VM is on its way to it. */
    static final class MigrationAwareHost extends HostSimple {
        MigrationAwareHost(long ram, long bandwidth, long storage, List<Pe> pes) {
            super(ram, bandwidth, storage, pes);
        }

        @Override
        public double updateProcessing(final double time) {
            final double deadline = getIdleShutdownDeadline();
            final boolean protect = Boolean.parseBoolean(System.getProperty("coe619.powerGuard", "true"))
                && !getVmsMigratingIn().isEmpty();
            if (protect) setIdleShutdownDeadline(-1);
            try {
                return super.updateProcessing(time);
            } finally {
                if (protect) setIdleShutdownDeadline(deadline);
            }
        }
    }

    public static final double UPPER_THRESHOLD = 0.80;
    public static final double IQR_SAFETY = 0.75;
    public static final int LR_WINDOW = 10;
    public static final double SCHEDULING_INTERVAL = 300;
    public static final double MIGRATION_PENALTY = 0.10;
    public static final double SATURATION = 0.999;

    private static final boolean TRACE = Boolean.getBoolean("coe619.trace");

    private static void trace(final String fmt, final Object... args) {
        if (TRACE) {
            System.out.println("  [trace] " + String.format(Locale.ROOT, fmt, args));
        }
    }

    private static final int HOST_PES = 8;
    private static final int HOST_MIPS = 1000;
    private static final int HOST_RAM = 16384;
    private static final long HOST_BW = 10_000;
    private static final long HOST_STORAGE = 1_000_000;
    private static final int VM_PES = 2;
    private static final int VM_MIPS = 1000;
    private static final int VM_RAM = 2048;
    private static final long VM_BW = 1000;
    private static final long VM_STORAGE = 10_000;

    /**
     * @param cloudletLength MI per PE; a negative value keeps cloudlets running until the
     *                       simulation ends (used for full-day PlanetLab runs)
     */
    public record Config(String scenario, Policy policy, List<Path> traces, int hosts,
                         long cloudletLength, double durationSeconds,
                         double idleShutdownSeconds, long seed) {
        public int vms() {
            return traces.size();
        }
    }

    public record Result(Config config, double energyJ, double slatah, double pdm, double slav,
                         double esv, double unmetDemand, int migrations, double activeHostHours,
                         int vmsCreated, int cloudletsFinished, double endTime,
                         long lrPredictions, long lrPredictiveTriggers) {

        public double energyKwh() {
            return energyJ / 3_600_000.0;
        }

        public static String csvHeader() {
            return "scenario,policy,seed,hosts,vms,energy_j,energy_kwh,slatah,pdm,slav,esv,"
                + "unmet_demand,migrations,active_host_hours,vms_created,cloudlets_finished,"
                + "end_time_s,lr_predictions,lr_predictive_triggers";
        }

        public String toCsv() {
            return String.format(Locale.ROOT,
                "%s,%s,%d,%d,%d,%.2f,%.6f,%.9e,%.9e,%.9e,%.9e,%.9e,%d,%.4f,%d,%d,%.1f,%d,%d",
                config.scenario(), config.policy(), config.seed(), config.hosts(), config.vms(),
                energyJ, energyKwh(), slatah, pdm, slav, esv, unmetDemand, migrations,
                activeHostHours, vmsCreated, cloudletsFinished, endTime, lrPredictions,
                lrPredictiveTriggers);
        }
    }

    private final Config config;
    private final CloudSimPlus simulation;
    private final List<Host> hosts = new ArrayList<>();
    private final List<Vm> vms = new ArrayList<>();
    private final Map<Vm, Double> migrationStart = new HashMap<>();
    private final Map<Vm, Host> migrationSource = new HashMap<>();
    private final Map<Vm, Double> migrationTime = new HashMap<>();
    private int migrations;
    private int inactiveWorkSamples;

    // Demand sampling (once per scheduling interval): MIPS x seconds summed over all VMs.
    private double demandMipsSeconds;
    private double unmetMipsSeconds;
    private double nextSample = SCHEDULING_INTERVAL;
    private List<Cloudlet> cloudletList = List.of();

    private VmAllocationPolicyMigration allocationPolicy;
    private DatacenterBroker broker;

    private ConsolidationExperiment(final Config config) {
        this.config = config;
        this.simulation = new CloudSimPlus();
    }

    public static Result run(final Config config) {
        return new ConsolidationExperiment(config).execute();
    }

    private Result execute() {
        Log.setLevel(ch.qos.logback.classic.Level.ERROR);
        if (Boolean.getBoolean("coe619.evaluateForecast")) {
            System.out.println("CASE_START scenario=" + config.scenario() + " policy=" + config.policy());
        }
        final boolean boundedObservation = Boolean.getBoolean("coe619.mixedJobs");
        if (!boundedObservation) simulation.terminateAt(config.durationSeconds());

        final DatacenterSimple dc = createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);
        if (Boolean.getBoolean("coe619.mixedJobs")) {
            // Separate lifecycle benchmark: completed batch VMs release their resources.
            broker.setVmDestructionDelay(1);
            System.out.println("MIXED_JOB_WORKLOAD shortMIperPE=5000000 longMIperPE=" + config.cloudletLength()
                + " longVmIndexModulo4=3 finishedVmDestructionDelaySeconds=1");
            System.out.println("MEASUREMENT_WINDOW start=0 end=" + config.durationSeconds()
                + " energyAndDemandBounded=true end_time_sRetainsActualClock=true");
        }
        broker.addOnVmsCreatedListener(info -> {
            allocationPolicy.setFindHostForVmFunction(null); // later placements use the policy
            broker.removeOnVmsCreatedListener(info.getListener());
        });

        createVms();
        broker.submitVmList(vms);
        final List<Cloudlet> cloudlets = createCloudlets();
        for (int i = 0; i < cloudlets.size(); i++) {
            broker.bindCloudletToVm(cloudlets.get(i), vms.get(i));
        }
        broker.submitCloudletList(cloudlets);

        final int n = vms.size();
        cloudletList = cloudlets;
        simulation.addOnClockTickListener(info -> {
            // The framework drains pending events after terminateAt. New experimental
            // migrations must not create further work outside their measurement window.
            if (Boolean.getBoolean("coe619.mixedJobs")
                && info.getTime() >= config.durationSeconds() && dc.isMigrationsEnabled()) {
                dc.disableMigrations();
                System.out.printf(Locale.ROOT, "MIGRATION_HORIZON_REACHED time=%.3f limit=%.3f%n",
                    info.getTime(), config.durationSeconds());
            }
            sampleDemand(info.getTime());
        });

        if (boundedObservation) {
            // Public synchronous API avoids the framework's unbounded final-event
            // drain when an experimental policy keeps scheduling future actions.
            simulation.startSync();
            while (simulation.clock() < config.durationSeconds()) {
                simulation.runFor(config.durationSeconds() - simulation.clock());
            }
            dc.disableMigrations();
            hosts.forEach(host -> host.updateProcessing(config.durationSeconds()));
            sampleDemand(config.durationSeconds());
            simulation.abort(); // intentional observation cutoff, not a failed experiment
            System.out.printf(Locale.ROOT, "BOUNDED_OBSERVATION_COMPLETE time=%.3f%n", simulation.clock());
        } else {
            simulation.start();
        }

        if (Boolean.getBoolean("coe619.evaluateForecast") && allocationPolicy instanceof PredictiveLinearRegressionPolicy predictor) {
            predictor.finishForecastEvaluation();
        }

        final int created = broker.getVmCreatedList().size();
        if (created != n) {
            throw new IllegalStateException("Only " + created + "/" + n + " VMs were created");
        }
        if (Boolean.getBoolean("coe619.auditPower")) {
            System.out.printf(Locale.ROOT, "POWER_AUDIT scenario=%s policy=%s inactiveWorkSamples=%d%n",
                config.scenario(), config.policy(), inactiveWorkSamples);
        }
        return computeResult(dc, cloudlets);
    }

    /**
     * Once per scheduling interval, compares each running VM's trace demand with the MIPS
     * its host actually allocated. Guarded so the cost is one pass over the VMs per interval,
     * not per clock tick.
     */
    private void sampleDemand(final double now) {
        if (now < nextSample) {
            return;
        }
        nextSample += SCHEDULING_INTERVAL;
        final double sampleSeconds = Boolean.getBoolean("coe619.mixedJobs")
            ? Math.max(0, Math.min(SCHEDULING_INTERVAL, config.durationSeconds() - now)) : SCHEDULING_INTERVAL;
        for (int i = 0; i < vms.size(); i++) {
            final Vm vm = vms.get(i);
            final Cloudlet c = cloudletList.get(i);
            if (c.getStatus() != Cloudlet.Status.INEXEC || vm.getHost() == Host.NULL) {
                continue;
            }
            final double demand = vm.getCpuPercentRequested() * vm.getTotalMipsCapacity();
            final double allocated = vm.getHost().getVmScheduler().getTotalAllocatedMipsForVm(vm);
            if (Boolean.getBoolean("coe619.auditPower") && !vm.getHost().isActive() && demand > 1e-9 && allocated > 1e-9) {
                inactiveWorkSamples++;
                if (inactiveWorkSamples <= 8) {
                    System.out.printf(Locale.ROOT,
                        "POWER_INVARIANT time=%.3f host=%d vm=%d demand=%.3f allocated=%.3f%n",
                        now, vm.getHost().getId(), vm.getId(), demand, allocated);
                }
            }
            demandMipsSeconds += demand * sampleSeconds;
            unmetMipsSeconds += Math.max(0, demand - allocated) * sampleSeconds;
        }
    }

    private Result computeResult(final DatacenterSimple dc, final List<Cloudlet> cloudlets) {
        double energy = 0;
        double slatahSum = 0;
        int activeHosts = 0;
        double activeSeconds = 0;
        int inactiveCpuEntries = 0;
        final boolean boundedWindow = Boolean.getBoolean("coe619.mixedJobs");
        for (final Host host : hosts) {
            // The framework exposes a LinkedList: indexed access would be quadratic.
            final List<HostStateHistoryEntry> h = new ArrayList<>(host.getStateHistory());
            if (Boolean.getBoolean("coe619.auditPower")) {
                for (final HostStateHistoryEntry entry : h) {
                    if (!entry.active() && entry.allocatedMips() > 1e-9) inactiveCpuEntries++;
                }
            }
            double active = 0;
            double saturated = 0;
            for (int i = 1; i < h.size(); i++) {
                final HostStateHistoryEntry prev = h.get(i - 1);
                final HostStateHistoryEntry curr = h.get(i);
                if (!prev.active()) {
                    continue;
                }
                final double dt = (boundedWindow ? Math.min(curr.time(), config.durationSeconds()) : curr.time()) - prev.time();
                if (dt <= 0) continue;
                final double uPrev = HostCpuUtilization.fractionOfCapacity(host, prev);
                final double uCurr = curr.active() ? HostCpuUtilization.fractionOfCapacity(host, curr) : uPrev;
                final double uEnd = boundedWindow && curr.time() > config.durationSeconds()
                    ? uPrev + (uCurr - uPrev) * dt / (curr.time() - prev.time()) : uCurr;
                energy += host.getPowerModel().getPower((uPrev + uEnd) / 2.0) * dt;
                active += dt;
                if (uPrev >= SATURATION) {
                    saturated += dt;
                }
            }
            if (active > 0) {
                slatahSum += saturated / active;
                activeHosts++;
                activeSeconds += active;
            }
        }
        final double slatah = activeHosts > 0 ? slatahSum / activeHosts : 0;

        double pdmSum = 0;
        int pdmVms = 0;
        for (int i = 0; i < vms.size(); i++) {
            final Vm vm = vms.get(i);
            double requestedInstructions = 0;
            for (org.cloudsimplus.cloudlets.CloudletExecution cle : vm.getCloudletScheduler().getCloudletFinishedList()) {
                requestedInstructions += cle.getCloudlet().getTotalLength();
            }
            if (requestedInstructions <= 0) {
                requestedInstructions = vm.getTotalMipsCapacity() * vm.getTotalExecutionTime();
            }
            if (requestedInstructions > 0) {
                final double degraded = MIGRATION_PENALTY * vm.getTotalMipsCapacity()
                    * migrationTime.getOrDefault(vm, 0.0);
                pdmSum += degraded / requestedInstructions;
                pdmVms++;
            }
        }
        final double pdm = pdmVms > 0 ? pdmSum / pdmVms : 0;
        final double slav = slatah * pdm;
        final double energyKwh = energy / 3_600_000.0;
        final double unmet = demandMipsSeconds > 0 ? unmetMipsSeconds / demandMipsSeconds : 0;

        long predictions = 0;
        long triggers = 0;
        if (allocationPolicy instanceof PredictiveLinearRegressionPolicy lr) {
            predictions = lr.getPredictionCount();
            triggers = lr.getPredictiveTriggerCount();
        }

        final int finished = (int) broker.getCloudletFinishedList().stream()
            .filter(c -> c.getStatus() == Cloudlet.Status.SUCCESS)
            .filter(c -> !boundedWindow || c.getFinishTime() <= config.durationSeconds()).count();
        if (Boolean.getBoolean("coe619.auditPower")) {
            System.out.printf(Locale.ROOT, "POWER_HISTORY_AUDIT scenario=%s policy=%s inactiveCpuEntries=%d%n",
                config.scenario(), config.policy(), inactiveCpuEntries);
        }
        return new Result(config, energy, slatah, pdm, slav, energyKwh * slav, unmet, migrations,
            activeSeconds / 3600.0, broker.getVmCreatedList().size(), finished,
            simulation.clock(), predictions, triggers);
    }

    private DatacenterSimple createDatacenter() {
        for (int i = 0; i < config.hosts(); i++) {
            final List<Pe> pes = new ArrayList<>(HOST_PES);
            for (int j = 0; j < HOST_PES; j++) {
                pes.add(new PeSimple(HOST_MIPS));
            }
            final Host host = new MigrationAwareHost(HOST_RAM, HOST_BW, HOST_STORAGE, pes);
            host.setRamProvisioner(new HostLocalResourceProvisioner(host.getRamProvisioner().getPmResource()));
            host.setBwProvisioner(new HostLocalResourceProvisioner(host.getBwProvisioner().getPmResource()));
            host.setVmScheduler(new VmSchedulerTimeShared());
            host.setPowerModel(new PowerModelHostSimple(250, 175));
            host.setStateHistoryEnabled(true);
            if (config.idleShutdownSeconds() >= 0) {
                host.setIdleShutdownDeadline(config.idleShutdownSeconds());
            }
            hosts.add(host);
        }

        final DatacenterSimple dc = new DatacenterSimple(simulation, hosts);
        allocationPolicy = createPolicy(config.policy());
        // Diagnostic environment only: isolate forecasting from the CloudSim 8.0.0
        // underloaded-host immutable-map defect. Applies to every policy equally.
        if (Boolean.getBoolean("coe619.noUnderload")) {
            allocationPolicy.setUnderUtilizationThreshold(1e-9);
        }
        ((VmAllocationPolicyMigrationAbstract) allocationPolicy).setFindHostForVmFunction(
            (policy, vm) -> policy.getHostList().stream()
                .map(h -> (Host) h)
                .filter(h -> h.isSuitableForVm(vm))
                .findFirst());
        dc.setVmAllocationPolicy(allocationPolicy);
        if (Boolean.getBoolean("coe619.noMigrations")) {
            dc.disableMigrations();
            System.out.printf(Locale.ROOT, "NO_MIGRATION_REFERENCE scenario=%s policyLabel=%s%n", config.scenario(), config.policy());
        }
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        dc.addOnVmMigrationFinishListener(info -> {
            final Vm vm = info.getVm();
            final Double start = migrationStart.remove(vm);
            final Host source = migrationSource.remove(vm);
            if (info.isMigrationSuccessful() && allocationPolicy instanceof PredictiveLinearRegressionPolicy lr) {
                if (source != null) lr.resetAfterMigration(source, info.getTime());
                lr.resetAfterMigration(vm.getHost(), info.getTime());
            }
            if (info.isMigrationSuccessful() && start != null) {
                migrations++;
                migrationTime.merge(vm, info.getTime() - start, Double::sum);
                if (Boolean.getBoolean("coe619.predictorDiagnostics")) {
                    System.out.printf(Locale.ROOT,
                        "MIGRATION_AUDIT scenario=%s policy=%s vm=%d start=%.3f finish=%.3f source=%d destination=%d%n",
                        config.scenario(), config.policy(), vm.getId(), start, info.getTime(),
                        source == null ? -1 : source.getId(), vm.getHost().getId());
                }
            }
            trace("t=%.0f migrated VM %d -> host %d", info.getTime(), vm.getId(), info.getVm().getHost().getId());
        });
        if (TRACE) {
            for (final Host h : hosts) {
                h.addOnShutdownListener(e -> trace("t=%.0f host %d shut down", e.getTime(), e.getHost().getId()));
                h.addOnStartupListener(e -> trace("t=%.0f host %d started", e.getTime(), e.getHost().getId()));
            }
        }
        return dc;
    }

    static VmAllocationPolicyMigration createPolicy(final Policy policy) {
        return switch (policy) {
            case THR -> new VmAllocationPolicyMigrationStaticThreshold(
                new VmSelectionPolicyMinimumMigrationTime(), UPPER_THRESHOLD);
            case LR -> new PredictiveLinearRegressionPolicy(
                new VmSelectionPolicyMinimumMigrationTime(), UPPER_THRESHOLD, LR_WINDOW, SCHEDULING_INTERVAL);
            case IQR -> new VmAllocationPolicyMigrationDynamicUpperThresholdFirstFit(
                new VmSelectionPolicyMinimumMigrationTime(), IQR_SAFETY,
                new VmAllocationPolicyMigrationStaticThreshold(
                    new VmSelectionPolicyMinimumMigrationTime(), UPPER_THRESHOLD)) {
                @Override
                public double computeHostUtilizationMeasure(final Host host) {
                    final DescriptiveStatistics stats = new DescriptiveStatistics();
                    for (final HostStateHistoryEntry e : host.getStateHistory()) {
                        if (e.active()) {
                            stats.addValue(HostCpuUtilization.fractionOfCapacity(host, e));
                        }
                    }
                    if (stats.getN() < 2) {
                        throw new IllegalStateException("Insufficient active-host samples for IQR");
                    }
                    return stats.getPercentile(75) - stats.getPercentile(25);
                }

                @Override
                public double getOverUtilizationThreshold(final Host host) {
                    final double t = super.getOverUtilizationThreshold(host);
                    trace("t=%.0f host %d IQR threshold=%.3f", host.getSimulation().clock(), host.getId(), t);
                    return t;
                }
            };
        };
    }

    private void createVms() {
        for (int i = 0; i < config.vms(); i++) {
            final Vm vm = new VmSimple(VM_MIPS, VM_PES);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_STORAGE);
            vm.addOnMigrationStartListener(info -> {
                migrationStart.put(vm, info.getTime());
                migrationSource.put(vm, vm.getHost());
            });
            vms.add(vm);
        }
    }

    private List<Cloudlet> createCloudlets() {
        final List<Cloudlet> list = new ArrayList<>();
        for (int i = 0; i < config.traces().size(); i++) {
            final Path trace = config.traces().get(i);
            final long length = Boolean.getBoolean("coe619.mixedJobs") && i % 4 != 3
                ? 5_000_000 : config.cloudletLength();
            final Cloudlet c = new CloudletSimple(length, VM_PES);
            c.setUtilizationModelCpu(new UtilizationModelPlanetLab(trace.toString(), SCHEDULING_INTERVAL));
            list.add(c);
        }
        return list;
    }

    /** Lists trace files in a directory in a stable (name-sorted) order. */
    public static List<Path> listTraces(final Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list traces in " + dir, e);
        }
    }

}
