package kfupm.coe619;

import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.builders.tables.CloudletsTableBuilder;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostStateHistoryEntry;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicyMinimumMigrationTime;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelPlanetLab;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Baseline 1: Reactive VM Consolidation with Static Threshold (THR).
 *
 * Research Citation:
 * A. Beloglazov and R. Buyya, "Optimal online deterministic algorithms and adaptive
 * heuristics for energy and performance efficient dynamic consolidation of virtual machines
 * in Cloud data centers," Concurrency and Computation: Practice and Experience,
 * vol. 24, no. 13, pp. 1397-1420, 2012. DOI: 10.1002/cpe.1867.
 *
 * Physical Configuration & Workload Density:
 * - Hosts: 4 physical hosts, each with 8 PEs @ 1000 MIPS (Total DC capacity: 32,000 MIPS).
 * - VMs: 8 VMs, each with 2 PEs @ 1000 MIPS (Total peak demand: 16,000 MIPS).
 * - Packing ratio: 4 VMs per host (8 PEs demand on 8 PEs capacity = up to 100% capacity under peak trace).
 * - Consolidation policy: Static upper CPU threshold at 80% (0.80).
 */
public class BaselineReactiveSimulation {
    private static final int HOSTS = 4;
    private static final int HOST_PES = 8;
    private static final int HOST_MIPS = 1000;
    private static final int HOST_RAM = 16384; // 16 GB RAM per host
    private static final long HOST_BW = 10_000;
    private static final long HOST_STORAGE = 1000000;

    private static final int VMS = 8;
    private static final int VM_PES = 2;
    private static final int VM_MIPS = 1000;
    private static final int VM_RAM = 2048; // 2 GB RAM per VM
    private static final long VM_BW = 1000; // 1 Gbps
    private static final long VM_STORAGE = 10000;

    private static final int CLOUDLETS = 8;
    private static final long CLOUDLET_LENGTH = 20_000_000; // MI per PE: long enough (~11 h) to span many 5-min scheduling intervals

    // Static CPU utilization threshold for migration trigger (80%)
    private static final double STATIC_THRESHOLD = 0.80;

    private final CloudSimPlus simulation;
    private DatacenterBroker broker;
    private Datacenter datacenter;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;

    // Tracking metrics
    private int migrationCount = 0;
    private final Map<Vm, Double> vmMigrationTimeMap = new HashMap<>();
    private final Map<Vm, Double> vmMigrationStartTimeMap = new HashMap<>();

    public static void main(String[] args) {
        new BaselineReactiveSimulation();
    }

    private BaselineReactiveSimulation() {
        Log.setLevel(ch.qos.logback.classic.Level.WARN);
        System.out.println("=== COE 619: Baseline Reactive Simulation (Static Threshold - THR) ===");

        simulation = new CloudSimPlus();
        simulation.terminateAt(86_400); // one synthetic-trace day

        datacenter = createDatacenter();
        final var allocationPolicy =
            (VmAllocationPolicyMigrationStaticThreshold) datacenter.getVmAllocationPolicy();

        broker = new DatacenterBrokerSimple(simulation);
        // Dense first-fit is only for admission. After VMs exist, restore the
        // policy default (null function) so migration destinations exclude the
        // source host and refuse hosts that would exceed STATIC_THRESHOLD.
        broker.addOnVmsCreatedListener(info -> {
            allocationPolicy.setFindHostForVmFunction(null);
            broker.removeOnVmsCreatedListener(info.getListener());
        });

        vmList = createVms();
        broker.submitVmList(vmList);

        cloudletList = createCloudlets();
        for (int i = 0; i < cloudletList.size(); i++) {
            broker.bindCloudletToVm(cloudletList.get(i), vmList.get(i));
        }
        broker.submitCloudletList(cloudletList);

        simulation.start();
        validateSimulationResults();

        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();

        printMetrics();
    }

    private void printMetrics() {
        List<Host> hostList = datacenter.getHostList();

        // =========================================================================
        // 1. ENERGY CONSUMPTION CALCULATION
        // Formula: Integral of power model P(u(t)) dt across active host intervals.
        // Power model: Linear interpolation between static power (175W) and peak (250W).
        // =========================================================================
        double totalEnergyJoules = 0;
        for (Host host : hostList) {
            totalEnergyJoules += calculateEnergyConsumption(host);
        }
        double energyKwh = totalEnergyJoules / 3600000.0; // 1 kWh = 3.6 x 10^6 Joules

        // =========================================================================
        // 2. SLA VIOLATION METRICS (Beloglazov & Buyya, 2012)
        // -------------------------------------------------------------------------
        // A) SLATAH: SLA Time per Active Host
        // Definition: Average fraction of active time during which hosts experienced
        // 100% CPU utilization saturation (U >= 1.0), leading to computational throttling.
        // Formula: (1 / N) * sum_{i=1}^{N} ( T_{s_i} / T_{a_i} )
        // -------------------------------------------------------------------------
        double sumSlatahRatios = 0;
        int activeHostCount = 0;
        for (Host host : hostList) {
            List<HostStateHistoryEntry> history = host.getStateHistory();
            double hostActiveTime = 0;
            double hostSaturationTime = 0;
            for (int i = 1; i < history.size(); i++) {
                HostStateHistoryEntry prev = history.get(i - 1);
                HostStateHistoryEntry curr = history.get(i);
                double interval = curr.time() - prev.time();
                if (prev.active()) {
                    hostActiveTime += interval;
                    // Strict saturation condition: CPU usage >= 100% (1.0)
                    if (prev.percentUsage() >= 0.999) {
                        hostSaturationTime += interval;
                    }
                }
            }
            if (hostActiveTime > 0) {
                sumSlatahRatios += (hostSaturationTime / hostActiveTime);
                activeHostCount++;
            }
        }
        double slatah = (activeHostCount > 0) ? (sumSlatahRatios / activeHostCount) : 0.0;

        // -------------------------------------------------------------------------
        // B) PDM: Performance Degradation due to Migrations
        // Definition: Average fraction of requested workload MIPS capacity lost
        // by VMs due to live migration pre-copy CPU and network transfer overhead.
        // Formula: (1 / M) * sum_{j=1}^{M} ( C_{d_j} / C_{r_j} )
        // Where:
        // C_{d_j} = 10% * Total_MIPS * Migration_Time (Beloglazov 10% empirical penalty)
        // C_{r_j} = Finished workload length in Million Instructions (MI)
        // -------------------------------------------------------------------------
        double sumPdmRatios = 0;
        int activeVmCount = 0;
        for (Vm vm : vmList) {
            double migrationTime = vmMigrationTimeMap.getOrDefault(vm, 0.0);
            double degradationMipsTime = 0.10 * vm.getTotalMipsCapacity() * migrationTime;

            // Total instructions requested by this VM across execution
            double requestedInstructions = 0;
            for (org.cloudsimplus.cloudlets.CloudletExecution cle : vm.getCloudletScheduler().getCloudletFinishedList()) {
                requestedInstructions += cle.getCloudlet().getLength();
            }
            if (requestedInstructions <= 0) {
                requestedInstructions = vm.getTotalMipsCapacity() * vm.getTotalExecutionTime();
            }

            if (requestedInstructions > 0) {
                sumPdmRatios += (degradationMipsTime / requestedInstructions);
                activeVmCount++;
            }
        }
        double pdm = (activeVmCount > 0) ? (sumPdmRatios / activeVmCount) : 0.0;

        // -------------------------------------------------------------------------
        // C) SLAV: Overall SLA Violation Composite Metric
        // Formula: SLAV = SLATAH * PDM
        // -------------------------------------------------------------------------
        double slav = slatah * pdm;

        // =========================================================================
        // 3. VM MIGRATIONS COUNT
        // Total number of live migrations successfully completed during the run.
        // =========================================================================
        int migrations = migrationCount;

        // =========================================================================
        // 4. ESV: Energy and SLA Violations (Joint Benchmark Objective)
        // Formula: ESV = Energy (kWh) * SLAV  (Lower is strictly better)
        // =========================================================================
        double esv = energyKwh * slav;
        int completedCloudlets = broker.getCloudletFinishedList().size();
        int createdVms = broker.getVmCreatedList().size();

        // --- Print Standardized Metrics Box ---
        System.out.println("\n╔════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  BASELINE THR - SIMULATION METRICS                     ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Energy Consumption                   : %10.2f J (%6.4f kWh)    ║\n", totalEnergyJoules, energyKwh);
        System.out.printf("║  SLATAH (SLA Time per Active Host)    : %14.6f                ║\n", slatah);
        System.out.printf("║  PDM (Degradation due to Migration)   : %14.9e             ║\n", pdm);
        System.out.printf("║  SLAV (Overall SLA Violation: S * P)  : %14.9e             ║\n", slav);
        System.out.printf("║  VMs Created / Expected                : %8d / %-8d          ║\n", createdVms, VMS);
        System.out.printf("║  Cloudlets Completed / Expected        : %8d / %-8d          ║\n", completedCloudlets, CLOUDLETS);
        System.out.printf("║  VM Migrations                        : %14d                ║\n", migrations);
        System.out.printf("║  ESV (Energy [kWh] * SLAV)            : %14.9e             ║\n", esv);
        System.out.println("╚════════════════════════════════════════════════════════════════════════╝");
    }

    private void validateSimulationResults() {
        final int createdVms = broker.getVmCreatedList().size();
        final List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
        final long successfulCloudlets = finishedCloudlets.stream()
            .filter(cloudlet -> cloudlet.getStatus() == Cloudlet.Status.SUCCESS)
            .count();

        if (createdVms != VMS || finishedCloudlets.size() != CLOUDLETS
            || successfulCloudlets != CLOUDLETS) {
            throw new IllegalStateException(
                "Simulation accounting incomplete: created " + createdVms + "/" + VMS
                    + " VMs and successfully finished " + successfulCloudlets + "/" + CLOUDLETS
                    + " cloudlets.");
        }
    }

    private Datacenter createDatacenter() {
        List<Host> hostList = new ArrayList<>(HOSTS);
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>(HOST_PES);
            for (int j = 0; j < HOST_PES; j++) {
                peList.add(new PeSimple(HOST_MIPS));
            }
            Host host = new HostSimple(HOST_RAM, HOST_BW, HOST_STORAGE, peList);
            host.setRamProvisioner(
                new HostLocalResourceProvisioner(host.getRamProvisioner().getPmResource()));
            host.setBwProvisioner(
                new HostLocalResourceProvisioner(host.getBwProvisioner().getPmResource()));
            host.setVmScheduler(new VmSchedulerTimeShared());
            host.setPowerModel(new PowerModelHostSimple(250, 175)); // Max: 250W, Static: 175W
            host.setStateHistoryEnabled(true);
            hostList.add(host);
        }

        DatacenterSimple dc = new DatacenterSimple(simulation, hostList);

        VmAllocationPolicyMigrationStaticThreshold allocationPolicy =
            new VmAllocationPolicyMigrationStaticThreshold(
                new VmSelectionPolicyMinimumMigrationTime(),
                STATIC_THRESHOLD);

        // Initial placement only: first-fit by PE/RAM, ignoring the 80% threshold so
        // 4 x 2-PE VMs can land on an 8-PE host (100% requested MIPS). getFindHostForVmFunction()
        // is null by default; restoring null after create uses defaultFindHostForVm for migrations.
        allocationPolicy.setFindHostForVmFunction((policy, vm) -> policy.getHostList().stream()
            .map(h -> (Host) h)
            .filter(h -> h.isSuitableForVm(vm))
            .findFirst());

        dc.setVmAllocationPolicy(allocationPolicy);
        dc.setSchedulingInterval(300); // 5-minute trace sampling interval

        // Accurate live migration listener
        dc.addOnVmMigrationFinishListener(eventInfo -> {
            final Vm vm = eventInfo.getVm();
            final Double migrationStartTime = vmMigrationStartTimeMap.remove(vm);
            if (eventInfo.isMigrationSuccessful()) {
                migrationCount++;
                if (migrationStartTime == null) {
                    throw new IllegalStateException("Migration completed without a recorded start time for " + vm);
                }
                final double duration = eventInfo.getTime() - migrationStartTime;
                if (duration <= 0) {
                    throw new IllegalStateException("Migration duration must be positive for " + vm);
                }
                vmMigrationTimeMap.merge(vm, duration, Double::sum);
            }
        });

        return dc;
    }

    private List<Vm> createVms() {
        List<Vm> list = new ArrayList<>(VMS);
        for (int i = 0; i < VMS; i++) {
            Vm vm = new VmSimple(VM_MIPS, VM_PES);
            vm.setRam(VM_RAM).setBw(VM_BW).setSize(VM_STORAGE);
            vm.addOnMigrationStartListener(info -> vmMigrationStartTimeMap.put(vm, info.getTime()));
            list.add(vm);
        }
        return list;
    }

    private List<Cloudlet> createCloudlets() {
        List<Cloudlet> list = new ArrayList<>(CLOUDLETS);
        for (int i = 0; i < CLOUDLETS; i++) {
            Cloudlet cloudlet = new CloudletSimple(CLOUDLET_LENGTH, VM_PES);
            // Distribute workload traces across the VMs
            int traceId = i; // one diurnal trace per VM
            String tracePath = "src/main/resources/planetlab/diurnal/trace_" + traceId + ".txt";
            UtilizationModelPlanetLab cpuUtilizationModel =
                new UtilizationModelPlanetLab(tracePath, 300);
            cloudlet.setUtilizationModelCpu(cpuUtilizationModel);
            list.add(cloudlet);
        }
        return list;
    }

    private double calculateEnergyConsumption(Host host) {
        List<HostStateHistoryEntry> history = host.getStateHistory();
        double energy = 0;
        for (int i = 1; i < history.size(); i++) {
            HostStateHistoryEntry previous = history.get(i - 1);
            HostStateHistoryEntry current = history.get(i);
            if (previous.active()) {
                double averageUtilization = (previous.percentUsage() + current.percentUsage()) / 2.0;
                energy += host.getPowerModel().getPower(averageUtilization)
                    * (current.time() - previous.time());
            }
        }
        return energy;
    }
}
