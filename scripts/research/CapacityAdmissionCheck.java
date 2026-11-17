package kfupm.coe619;

import java.util.ArrayList;
import java.util.List;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

/** Checks the current scheduler's admission limit separately from trace CPU usage. */
public final class CapacityAdmissionCheck {
    public static void main(String[] args) {
        var simulation = new CloudSimPlus();
        List<Pe> pes = new ArrayList<>();
        for (int i = 0; i < 8; i++) pes.add(new PeSimple(1000));
        var host = new HostSimple(16384, 10000, 1000000, pes);
        host.setRamProvisioner(new HostLocalResourceProvisioner(host.getRamProvisioner().getPmResource()));
        host.setBwProvisioner(new HostLocalResourceProvisioner(host.getBwProvisioner().getPmResource()));
        host.setVmScheduler(new VmSchedulerTimeShared());
        new DatacenterSimple(simulation, List.of(host));
        for (int i = 0; i < 4; i++) {
            if (!host.createVm(vm(i)).fully()) throw new AssertionError("Expected admission of VM " + i);
        }
        Vm fifth = vm(4);
        if (host.getVmScheduler().isSuitableForVm(fifth)) throw new AssertionError("Unexpected CPU admission for fifth VM");
        if (host.isSuitableForVm(fifth)) throw new AssertionError("Unexpected full admission for fifth VM");
        if (host.getRam().getAvailableResource() < 2048 || host.getBw().getAvailableResource() < 1000) {
            throw new AssertionError("Test must leave enough non-CPU resources");
        }
        if (host.getCpuPercentUtilization() != 0) throw new AssertionError("No cloudlet CPU work expected");
        System.out.println("PASS: four idle VMs reserve all 8000 host MIPS; fifth rejected by CPU admission despite spare RAM/BW.");
        System.out.println("Trace CPU usage and reserved virtual CPU capacity are different quantities.");
    }

    private static Vm vm(int id) {
        return new VmSimple(id, 1000, 2).setRam(2048).setBw(1000).setSize(10000);
    }
}
