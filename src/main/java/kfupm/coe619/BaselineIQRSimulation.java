package kfupm.coe619;

import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationMedianAbsoluteDeviation;
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
 * Baseline Reactive Simulation using Median Absolute Deviation (MAD) policy.
 * MAD dynamically computes the overload threshold based on statistical deviation
 * of CPU utilization history, unlike the fixed Static Threshold.
 * Outputs: Energy Consumption, SLA Violations (SLATAH, PDM, SLAV), VM Migrations, and ESV.
 */
public class BaselineIQRSimulation {
    private static final int HOSTS = 2;
    private static final int HOST_PES = 8;
    private static final int VMS = 4;
    private static final int VM_PES = 2;
    private static final int CLOUDLETS = 4;
    private static final long CLOUDLET_LENGTH = 100000;

    // Safety parameter for dynamic threshold calculation
    private static final double SAFETY_PARAMETER = 1.5;

    private final CloudSimPlus simulation;
    private DatacenterBroker broker;
    private Datacenter datacenter;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;

    public static void main(String[] args) {
        new BaselineIQRSimulation();
    }

    private BaselineIQRSimulation() {
        Log.setLevel(ch.qos.logback.classic.Level.WARN);
        System.out.println("=== COE 619: Baseline Reactive Simulation (MAD/IQR Dynamic Threshold) ===\n");

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
        double energyKwh = totalEnergy / 3600000.0;

        // --- 2. SLA Violations ---
        // SLATAH = fraction of time hosts were overloaded
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
                    // For MAD/IQR, consider overloaded when usage exceeds 100%
                    // (since the threshold is dynamic and set internally)
                    if (prev.percentUsage() > 1.0) {
                        totalOverloadTime += interval;
                    }
                }
            }
        }
        double slatah = (totalActiveTime > 0) ? totalOverloadTime / totalActiveTime : 0;

        // PDM = Performance Degradation due to Migration
        double totalRequestedMips = 0;
        double totalUnderAllocatedMips = 0;
        for (Vm vm : vmList) {
            double requested = vm.getTotalMipsCapacity() * vm.getTotalExecutionTime();
            totalRequestedMips += requested;
            double cpuUtil = vm.getCpuPercentUtilization();
            if (cpuUtil > 0.95) {
                totalUnderAllocatedMips += requested * 0.1;
            }
        }
        double pdm = (totalRequestedMips > 0) ? totalUnderAllocatedMips / totalRequestedMips : 0;

        // SLAV = SLATAH * PDM
        double slav = slatah * pdm;

        // --- 3. VM Migrations ---
        int migrations = (int) datacenter.getMigrationCount();

        // --- 4. ESV = Energy * SLAV
        double esv = energyKwh * slav;

        // --- Print Results ---
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║        BASELINE MAD/IQR - SIMULATION METRICS            ║");
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

        // MAD (Median Absolute Deviation) dynamic threshold policy
        VmAllocationPolicyMigrationMedianAbsoluteDeviation allocationPolicy =
            new VmAllocationPolicyMigrationMedianAbsoluteDeviation(
                new VmSelectionPolicyMinimumMigrationTime(),
                SAFETY_PARAMETER);

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
