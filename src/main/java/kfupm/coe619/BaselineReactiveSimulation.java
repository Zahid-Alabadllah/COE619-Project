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

    public static void main(String[] args) {
        new BaselineReactiveSimulation();
    }

    private BaselineReactiveSimulation() {
        Log.setLevel(ch.qos.logback.classic.Level.WARN);
        System.out.println("=== COE 619: Baseline Reactive Simulation (Static Threshold - THR) ===\n");

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

        // --- 1. Energy Consumption ---
        double totalEnergy = 0;
        for (Host host : hostList) {
            totalEnergy += calculateEnergyConsumption(host);
        }
        double energyKwh = totalEnergy / 3600000.0; // Convert Joules to kWh

        // --- 2. SLA Violations ---
        // SLATAH = fraction of time hosts were overloaded (CPU > threshold)
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

        // PDM = Performance Degradation due to Migration
        // (ratio of requested MIPS that were not delivered during migration)
        double totalRequestedMips = 0;
        double totalUnderAllocatedMips = 0;
        for (Vm vm : vmList) {
            double requested = vm.getTotalMipsCapacity() * vm.getTotalExecutionTime();
            totalRequestedMips += requested;
            // Approximate: if VM was migrating, it loses some performance
            double cpuUtil = vm.getCpuPercentUtilization();
            if (cpuUtil > 0.95) {
                totalUnderAllocatedMips += requested * 0.1; // 10% degradation estimate
            }
        }
        double pdm = (totalRequestedMips > 0) ? totalUnderAllocatedMips / totalRequestedMips : 0;

        // SLAV = SLATAH * PDM
        double slav = slatah * pdm;

        // --- 3. VM Migrations ---
        int migrations = (int) datacenter.getMigrationCount();

        // --- 4. ESV = Energy * SLAV (lower is better)
        double esv = energyKwh * slav;

        // --- Print Results ---
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║          BASELINE THR - SIMULATION METRICS              ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf("║  Energy Consumption  : %12.2f Joules (%6.4f kWh) ║\n", totalEnergy, energyKwh);
        System.out.printf("║  SLATAH              : %12.6f                     ║\n", slatah);
        System.out.printf("║  PDM                 : %12.6f                     ║\n", pdm);
        System.out.printf("║  SLAV (SLATAH * PDM) : %12.6f                     ║\n", slav);
        System.out.printf("║  VM Migrations       : %12d                     ║\n", migrations);
        System.out.printf("║  ESV (Energy * SLAV) : %12.6f                     ║\n", esv);
        System.out.println("╚══════════════════════════════════════════════════════════╝");
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
