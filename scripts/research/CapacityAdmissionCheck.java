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
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationAbstract;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicyMinimumMigrationTime;

/** Checks the current scheduler's admission limit separately from trace CPU usage. */
public final class CapacityAdmissionCheck {
    public static void main(String[] args) throws Exception {
        var simulation = new CloudSimPlus();
        List<Pe> pes = new ArrayList<>();
        for (int i = 0; i < 8; i++) pes.add(new PeSimple(1000));
        var host = new HostSimple(16384, 10000, 1000000, pes);
        host.setRamProvisioner(new HostLocalResourceProvisioner(host.getRamProvisioner().getPmResource()));
        host.setBwProvisioner(new HostLocalResourceProvisioner(host.getBwProvisioner().getPmResource()));
        host.setVmScheduler(new VmSchedulerTimeShared());
        var datacenter = new DatacenterSimple(simulation, List.of(host));
        var policy = new VmAllocationPolicyMigrationStaticThreshold(new VmSelectionPolicyMinimumMigrationTime(), .8);
        datacenter.setVmAllocationPolicy(policy);
        for (int i = 0; i < 4; i++) {
            Vm candidate = vm(i);
            if (i == 3) {
                if (!host.isSuitableForVm(candidate)) throw new AssertionError("Fourth VM should physically fit");
                var check = VmAllocationPolicyMigrationAbstract.class.getDeclaredMethod("isNotHostOverloadedAfterAllocation", Host.class, Vm.class);
                check.setAccessible(true);
                boolean allowed = (boolean) check.invoke(policy, host, candidate);
                boolean expected = false;
                if (allowed != expected) throw new AssertionError("Unexpected fourth-VM migration eligibility: " + allowed);
                System.out.println("PASS: idle fourth VM migration allowed=" + allowed + "; physical CPU admission is checked separately.");
            }
            if (!host.createVm(candidate).fully()) throw new AssertionError("Expected admission of VM " + i);
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
