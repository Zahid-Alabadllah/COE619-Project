package kfupm.coe619;

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
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.util.Log;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

import java.util.ArrayList;
import java.util.List;

/**
 * Foundation for the COE 619 Project: Energy-Aware Predictive VM Consolidation.
 * This class sets up a basic CloudSim Plus environment to ensure dependencies and structure are working.
 */
public class BaselineSimulation {
    private static final int HOSTS = 2;
    private static final int HOST_PES = 4;
    private static final int VMS = 4;
    private static final int VM_PES = 2;
    private static final int CLOUDLETS = 4;
    private static final long CLOUDLET_LENGTH = 10000;

    private final CloudSimPlus simulation;
    private DatacenterBroker broker;
    private List<Vm> vmList;
    private List<Cloudlet> cloudletList;

    public static void main(String[] args) {
        new BaselineSimulation();
    }

    private BaselineSimulation() {
        // Suppress overwhelming logs for cleaner output
        Log.setLevel(ch.qos.logback.classic.Level.WARN);

        System.out.println("Starting COE 619 Baseline Simulation Environment...");

        simulation = new CloudSimPlus();
        createDatacenter();

        broker = new DatacenterBrokerSimple(simulation);
        
        vmList = createVms();
        broker.submitVmList(vmList);

        cloudletList = createCloudlets();
        broker.submitCloudletList(cloudletList);

        simulation.start();

        new CloudletsTableBuilder(broker.getCloudletFinishedList()).build();
        
        System.out.println("Simulation finished successfully. Environment is configured correctly!");
    }

    private Datacenter createDatacenter() {
        List<Host> hostList = new ArrayList<>(HOSTS);
        for (int i = 0; i < HOSTS; i++) {
            List<Pe> peList = new ArrayList<>(HOST_PES);
            for (int j = 0; j < HOST_PES; j++) {
                peList.add(new PeSimple(1000)); // 1000 MIPS per PE
            }
            Host host = new HostSimple(10000, 8000, 1000000, peList); // RAM, BW, Storage, PEs
            hostList.add(host);
        }
        return new DatacenterSimple(simulation, hostList);
    }

    private List<Vm> createVms() {
        List<Vm> list = new ArrayList<>(VMS);
        for (int i = 0; i < VMS; i++) {
            Vm vm = new VmSimple(1000, VM_PES); // 1000 MIPS, 2 PEs
            vm.setRam(1000).setBw(1000).setSize(10000);
            list.add(vm);
        }
        return list;
    }

    private List<Cloudlet> createCloudlets() {
        List<Cloudlet> list = new ArrayList<>(CLOUDLETS);
        for (int i = 0; i < CLOUDLETS; i++) {
            Cloudlet cloudlet = new CloudletSimple(CLOUDLET_LENGTH, VM_PES);
            list.add(cloudlet);
        }
        return list;
    }
}
