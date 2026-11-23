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

/** Reproduces physical placement mutation during underload planning without a real migration. */
public final class UnderloadPlanningCheck {
    public static void main(String[] args) throws Exception {
        var simulation = new CloudSimPlus();
        List<Host> hosts = new ArrayList<>();
        for (int h = 0; h < 2; h++) {
            List<Pe> pes = new ArrayList<>();
            for (int i = 0; i < 8; i++) pes.add(new PeSimple(1000));
            var host = new HostSimple(16384, 10000, 1000000, pes);
            host.setRamProvisioner(new HostLocalResourceProvisioner(host.getRamProvisioner().getPmResource()));
            host.setBwProvisioner(new HostLocalResourceProvisioner(host.getBwProvisioner().getPmResource()));
            host.setVmScheduler(new VmSchedulerTimeShared());
            hosts.add(host);
        }
        var dc = new DatacenterSimple(simulation, hosts);
        var policy = new VmAllocationPolicyMigrationStaticThreshold(new VmSelectionPolicyMinimumMigrationTime(), .8);
        dc.setVmAllocationPolicy(policy);
        Vm source = vm(0);
        if (!hosts.get(0).createVm(source).fully()) throw new AssertionError("Source admission");
        for (int i = 1; i <= 2; i++) if (!hosts.get(1).createVm(vm(i)).fully()) throw new AssertionError("Target admission");
        java.util.Map<Vm, Host> planned;
        if (args.length > 0 && args[0].equals("public")) {
            planned = policy.getOptimizedAllocationMap(List.of(source));
        } else {
            var method = VmAllocationPolicyMigrationAbstract.class.getDeclaredMethod(
                "updateMigrationMapFromUnderloadedHosts", java.util.Set.class, java.util.Map.class);
            method.setAccessible(true);
            planned = new java.util.LinkedHashMap<Vm, Host>();
            method.invoke(policy, java.util.Set.of(), planned);
        }
        long copies = hosts.stream().flatMap(h -> h.getVmList().stream()).filter(v -> v == source).count();
        if (planned.get(source) != hosts.get(1) || copies != 2 || source.isInMigration())
            throw new AssertionError("Known mutation not reproduced: copies=" + copies + " map=" + planned);
        System.out.println("REPRODUCED: planning added the same VM to two physical host lists before migration started.");
        System.out.println("Source contains VM=" + hosts.get(0).getVmList().contains(source)
            + "; target contains VM=" + hosts.get(1).getVmList().contains(source) + "; migration=" + source.isInMigration());
    }
    private static Vm vm(int id) { return new VmSimple(id,1000,2).setRam(2048).setBw(1000).setSize(10000); }
}
