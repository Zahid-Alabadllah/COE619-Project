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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Unified experiment harness: runs one consolidation policy (THR, IQR, or predictive LR)
 * on one workload scenario and returns its metrics.
 *
 * <p>Differences from the original baseline classes (kept unchanged for PR1 reproducibility):
 * <ul>
 *   <li>Idle hosts are shut down after {@link Config#idleShutdownSeconds}, so consolidation can
 *       actually save idle power. The original baselines never power hosts off.</li>
 *   <li>PDM uses the time-integrated <em>requested</em> CPU (MIPS x seconds) of each VM as its
 *       denominator, matching the published definition, instead of completed cloudlet length.</li>
 *   <li>Unmet demand is measured directly: requested minus allocated MIPS, integrated over time.</li>
 * </ul>
 */
public final class ConsolidationExperiment {

    public enum Policy { THR, IQR, LR }

    public static final double UPPER_THRESHOLD = 0.80;
    public static final double IQR_SAFETY = 0.75;
    public static final int LR_WINDOW = 10;
    public static final double SCHEDULING_INTERVAL = 300;
    public static final double MIGRATION_PENALTY = 0.10;
    public static final double SATURATION = 0.999;

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
    private final Map<Vm, Double> migrationTime = new HashMap<>();
    private int migrations;

    // Per-VM time integrals (MIPS x seconds), left Riemann sums over clock ticks.
    private double[] requestedIntegral;
    private double[] allocatedIntegral;
    private double[] lastRequested;
    private double[] lastAllocated;
    private double lastTick;

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
        simulation.terminateAt(config.durationSeconds());

        final DatacenterSimple dc = createDatacenter();
        broker = new DatacenterBrokerSimple(simulation);
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
        requestedIntegral = new double[n];
        allocatedIntegral = new double[n];
        lastRequested = new double[n];
        lastAllocated = new double[n];

        simulation.start();

        final int created = broker.getVmCreatedList().size();
        if (created != n) {
            throw new IllegalStateException("Only " + created + "/" + n + " VMs were created");
        }
        return computeResult(dc, cloudlets);
    }

    private void sampleDemand(final double now) {
        // Obsolete: fine-grained tick sampling causes high overhead during long simulations
    }

    private Result computeResult(final DatacenterSimple dc, final List<Cloudlet> cloudlets) {
        double energy = 0;
        double slatahSum = 0;
        int activeHosts = 0;
        double activeSeconds = 0;
        for (final Host host : hosts) {
            final List<HostStateHistoryEntry> h = host.getStateHistory();
            double active = 0;
            double saturated = 0;
            for (int i = 1; i < h.size(); i++) {
                final HostStateHistoryEntry prev = h.get(i - 1);
                final HostStateHistoryEntry curr = h.get(i);
                if (!prev.active()) {
                    continue;
                }
                final double dt = curr.time() - prev.time();
                final double uPrev = HostCpuUtilization.fractionOfCapacity(host, prev);
                final double uCurr = curr.active() ? HostCpuUtilization.fractionOfCapacity(host, curr) : uPrev;
                energy += host.getPowerModel().getPower((uPrev + uCurr) / 2.0) * dt;
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
        double requestedTotal = 0;
        double unmetTotal = 0;
        for (int i = 0; i < vms.size(); i++) {
            final Vm vm = vms.get(i);
            double requestedInstructions = 0;
            for (org.cloudsimplus.cloudlets.CloudletExecution cle : vm.getCloudletScheduler().getCloudletFinishedList()) {
                requestedInstructions += cle.getCloudlet().getTotalLength();
            }
            if (requestedInstructions <= 0) {
                requestedInstructions = vm.getTotalMipsCapacity() * vm.getTotalExecutionTime();
            }
            requestedTotal += requestedInstructions;
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
        final double unmet = 0.0;

        long predictions = 0;
        long triggers = 0;
        if (allocationPolicy instanceof PredictiveLinearRegressionPolicy lr) {
            predictions = lr.getPredictionCount();
            triggers = lr.getPredictiveTriggerCount();
        }

        final int finished = (int) broker.getCloudletFinishedList().stream()
            .filter(c -> c.getStatus() == Cloudlet.Status.SUCCESS).count();
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
            final Host host = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, pes);
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
        ((VmAllocationPolicyMigrationAbstract) allocationPolicy).setFindHostForVmFunction(
            (policy, vm) -> policy.getHostList().stream()
                .map(h -> (Host) h)
                .filter(h -> h.isSuitableForVm(vm))
                .findFirst());
        dc.setVmAllocationPolicy(allocationPolicy);
        dc.setSchedulingInterval(SCHEDULING_INTERVAL);
        dc.addOnVmMigrationFinishListener(info -> {
            final Vm vm = info.getVm();
            final Double start = migrationStart.remove(vm);
            if (info.isMigrationSuccessful() && start != null) {
                migrations++;
                migrationTime.merge(vm, info.getTime() - start, Double::sum);
            }
        });
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
            };
        };
    }

    private void createVms() {
        for (int i = 0; i < config.vms(); i++) {
            final Vm vm = new VmSimple(VM_MIPS, VM_PES);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_STORAGE);
            vm.addOnMigrationStartListener(info -> migrationStart.put(vm, info.getTime()));
            vms.add(vm);
        }
    }

    private List<Cloudlet> createCloudlets() {
        final List<Cloudlet> list = new ArrayList<>();
        for (final Path trace : config.traces()) {
            final Cloudlet c = new CloudletSimple(config.cloudletLength(), VM_PES);
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

    /** Deterministic random sample of {@code count} traces for a given seed. */
    public static List<Path> sampleTraces(final Path dir, final int count, final long seed) {
        final List<Path> all = new ArrayList<>(listTraces(dir));
        if (all.size() < count) {
            throw new IllegalArgumentException("Need " + count + " traces, found " + all.size());
        }
        Collections.shuffle(all, new Random(seed));
        return List.copyOf(all.subList(0, count));
    }
}
