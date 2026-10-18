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
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelPlanetLab;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicyMinimumMigrationTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline Reactive Simulation for COE 619 Project.
 * Uses Static Threshold (THR) policy: triggers migration when host CPU > 80%.
 * Outputs: Energy Consumption, SLA Violations (SLATAH, PDM, SLAV), VM Migrations, and ESV.
 */
public class BaselineReactiveSimulation {
    private static final int HOSTS = 2;
    private static final int HOST_PES = 8;
    private static final int VMS = 4;
    private static final int VM_PES = 2;
    private static final int CLOUDLETS = 4;
    private static final long CLOUDLET_LENGTH = 100000;

    // CPU usage threshold for reactive migration (80%)
    private static final double STATIC_THRESHOLD = 0.8;

    private final CloudSimPlus simulation;
    private DatacenterBroker broker;
    private Datacenter datacenter;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;
    private int migrationCount = 0;

    public static void main(String[] args) {
        new BaselineReactiveSimulation();
    }

    private BaselineReactiveSimulation() {
        Log.setLevel(ch.qos.logback.classic.Level.WARN);
        System.out.println("=== COE 619: Baseline Reactive Simulation (Static Threshold - THR) ===");

        simulation = new CloudSimPlus();
        datacenter = createDatacenter();

        broker = new DatacenterBrokerSimple(simulation);

        vmList = createVms();
        broker.submitVmList(vmList);

        cloudletList = createCloudlets();
        broker.submitCloudletList(cloudletList);

        simulation.start();

        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();

        printMetrics();
    }

    private void printMetrics() {
        List<Host> hostList = datacenter.getHostList();

        // =========================================================================
        // 1. ENERGY CONSUMPTION CALCULATION
        // Formula: Integrates power consumption over time intervals for each host.
        // Power Model uses linear active power between Static (175W) and Max (250W).
        // =========================================================================
        double totalEnergy = 0;
        for (Host host : hostList) {
            totalEnergy += calculateEnergyConsumption(host);
        }
        double energyKwh = totalEnergy / 3600000.0; // Convert Joules (Watt-sec) to kWh

        // =========================================================================
        // 2. SLA VIOLATIONS CALCULATION (Beloglazov et al., 2012)
        // -------------------------------------------------------------------------
        // A) SLATAH: SLA Time Affected by Host Overload
        //    Definition: Fraction of active time during which hosts experienced 
        //    CPU utilization > threshold (here fixed at 80% / 0.8).
        //    Formula: Total Overloaded Time / Total Active Time
        // -------------------------------------------------------------------------
        double totalActiveTime = 0;
        double totalOverloadTime = 0;
        for (Host host : hostList) {
            List<HostStateHistoryEntry> history = host.getStateHistory();
            for (int i = 1; i < history.size(); i++) {
                HostStateHistoryEntry prev = history.get(i - 1);
                HostStateHistoryEntry curr = history.get(i);
                double interval = curr.time() - prev.time();
                if (prev.active()) {
                    totalActiveTime += interval;
                    if (prev.percentUsage() > STATIC_THRESHOLD) {
                        totalOverloadTime += interval;
                    }
                }
            }
        }
        double slatah = (totalActiveTime > 0) ? totalOverloadTime / totalActiveTime : 0;

        // -------------------------------------------------------------------------
        // B) PDM: Performance Degradation due to Migration
        //    Definition: Overall performance degradation caused by migrating VMs.
        //    Formula: Total Underallocated MIPS during Migration / Total Requested MIPS
        //    Note: Heuristic approximation assuming ~10% capacity degradation during
        //    periods of peak load / migration events.
        // -------------------------------------------------------------------------
        double totalRequestedMips = 0;
        double totalUnderAllocatedMips = 0;
        for (Vm vm : vmList) {
            double requested = vm.getTotalMipsCapacity() * vm.getTotalExecutionTime();
            totalRequestedMips += requested;
            double cpuUtil = vm.getCpuPercentUtilization();
            if (cpuUtil > 0.95) {
                totalUnderAllocatedMips += requested * 0.1; // 10% degradation heuristic
            }
        }
        double pdm = (totalRequestedMips > 0) ? totalUnderAllocatedMips / totalRequestedMips : 0;

        // -------------------------------------------------------------------------
        // C) SLAV: Overall SLA Violation Metric
        //    Combines both host overload time and migration performance degradation.
        //    Formula: SLAV = SLATAH * PDM
        // -------------------------------------------------------------------------
        double slav = slatah * pdm;

        // =========================================================================
        // 3. VM MIGRATIONS COUNT
        // Tracked via Datacenter onVmMigrationFinish listener
        // =========================================================================
        int migrations = migrationCount;

        // =========================================================================
        // 4. ESV: Energy and SLA Violations (Combined Metric)
        // Joint optimization objective: evaluates trade-off between energy savings
        // and SLA penalties. Lower value indicates superior consolidation efficiency.
        // Formula: ESV = Energy (kWh) * SLAV
        // =========================================================================
        double esv = energyKwh * slav;

        // --- Print Summary Box ---
        System.out.println("\n╔════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  BASELINE THR - SIMULATION METRICS                     ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Energy Consumption                   : %10.2f J (%6.4f kWh)    ║\n", totalEnergy, energyKwh);
        System.out.printf("║  SLATAH (SLA Time Affected by Overload): %14.6f                ║\n", slatah);
        System.out.printf("║  PDM (Degradation due to Migration)   : %14.6f                ║\n", pdm);
        System.out.printf("║  SLAV (Overall SLA Violation)         : %14.6f                ║\n", slav);
        System.out.printf("║  VM Migrations                        : %14d                ║\n", migrations);
        System.out.printf("║  ESV (Energy * SLAV)                  : %14.6f                ║\n", esv);
        System.out.println("╚════════════════════════════════════════════════════════════════════════╝");
    }

    private Datacenter createDatacenter() {
        List<Host> hostList = new ArrayList<>(HOSTS);
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>(HOST_PES);
            for (int j = 0; j < HOST_PES; j++) {
                peList.add(new PeSimple(1000));
            }
            Host host = new HostSimple(10000, 8000, 1000000, peList);
            host.setPowerModel(new PowerModelHostSimple(250, 175));
            host.setStateHistoryEnabled(true);
            hostList.add(host);
        }

        DatacenterSimple dc = new DatacenterSimple(simulation, hostList);

        VmAllocationPolicyMigrationStaticThreshold allocationPolicy =
            new VmAllocationPolicyMigrationStaticThreshold(
                new VmSelectionPolicyMinimumMigrationTime(),
                STATIC_THRESHOLD);

        dc.setVmAllocationPolicy(allocationPolicy);
        dc.setSchedulingInterval(300);

        // Track live migrations accurately
        dc.addOnVmMigrationFinishListener(eventInfo -> {
            if (eventInfo.isMigrationSuccessful()) {
                migrationCount++;
            }
        });

        return dc;
    }

    private List<Vm> createVms() {
        List<Vm> list = new ArrayList<>(VMS);
        for (int i = 0; i < VMS; i++) {
            Vm vm = new VmSimple(1000, VM_PES);
            vm.setRam(1000).setBw(1000).setSize(10000);
            list.add(vm);
        }
        return list;
    }

    private List<Cloudlet> createCloudlets() {
        List<Cloudlet> list = new ArrayList<>(CLOUDLETS);
        for (int i = 0; i < CLOUDLETS; i++) {
            Cloudlet cloudlet = new CloudletSimple(CLOUDLET_LENGTH, VM_PES);
            String tracePath = "src/main/resources/planetlab/20110303/trace_" + i + ".txt";
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
                double averageUtilization = (previous.percentUsage() + current.percentUsage()) / 2;
                energy += host.getPowerModel().getPower(averageUtilization)
                    * (current.time() - previous.time());
            }
        }
        return energy;
    }
}
