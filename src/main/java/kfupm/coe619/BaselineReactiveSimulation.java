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
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.power.models.PowerModelHostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.utilizationmodels.UtilizationModelPlanetLabInMemory;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;
import org.cloudsimplus.vms.selection.VmSelectionPolicyMinimumMigrationTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline Reactive Simulation for COE 619 Project.
 * This class simulates a Data Center using standard reactive VM consolidation (Static Threshold).
 */
public class BaselineReactiveSimulation {
    private static final int HOSTS = 2;
    private static final int HOST_PES = 4;
    private static final int VMS = 4;
    private static final int VM_PES = 2;
    private static final int CLOUDLETS = 4;
    private static final long CLOUDLET_LENGTH = 100000;
    
    // CPU usage Threshold for reactive migration (e.g., 80%)
    private static final double STATIC_THRESHOLD = 0.8;

    private final CloudSimPlus simulation;
    private DatacenterBroker broker;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;

    public static void main(String[] args) {
        new BaselineReactiveSimulation();
    }

    private BaselineReactiveSimulation() {
        Log.setLevel(ch.qos.logback.classic.Level.WARN);
        System.out.println("Starting COE 619 Baseline Reactive Simulation (THR)...");

        simulation = new CloudSimPlus();
        createDatacenter();

        broker = new DatacenterBrokerSimple(simulation);
        
        vmList = createVms();
        broker.submitVmList(vmList);

        cloudletList = createCloudlets();
        broker.submitCloudletList(cloudletList);

        simulation.start();

        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
        
        System.out.println("\n--- Simulation Results ---");
        System.out.println("Energy Consumption and SLA Violation metrics will be processed here.");
        System.out.println("Simulation finished successfully.");
    }

    private Datacenter createDatacenter() {
        List<Host> hostList = new ArrayList<>(HOSTS);
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>(HOST_PES);
            for (int j = 0; j < HOST_PES; j++) {
                peList.add(new PeSimple(1000)); 
            }
            Host host = new HostSimple(10000, 8000, 1000000, peList);
            // Attach a simple power model to the host (Max power 250W, Static power 175W)
            host.setPowerModel(new PowerModelHostSimple(250, 175));
            hostList.add(host);
        }
        
        DatacenterSimple dc = new DatacenterSimple(simulation, hostList);
        
        // Setup Reactive VM Allocation Policy (Static Threshold)
        VmAllocationPolicyMigrationStaticThreshold allocationPolicy = 
            new VmAllocationPolicyMigrationStaticThreshold(
                new VmSelectionPolicyMinimumMigrationTime(), 
                STATIC_THRESHOLD);
                
        dc.setVmAllocationPolicy(allocationPolicy);
        dc.setSchedulingInterval(300); // 5 minutes interval for PlanetLab traces
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
            
            // Attach PlanetLab trace to the cloudlet for dynamic CPU utilization
            String tracePath = "src/main/resources/planetlab/20110303/trace_" + i + ".txt";
            UtilizationModelPlanetLabInMemory cpuUtilizationModel = 
                new UtilizationModelPlanetLabInMemory(tracePath, 300);
            
            cloudlet.setUtilizationModelCpu(cpuUtilizationModel);
            list.add(cloudlet);
        }
        return list;
    }
}
