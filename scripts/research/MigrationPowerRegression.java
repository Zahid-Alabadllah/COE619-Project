package kfupm.coe619;

import java.util.ArrayList;
import java.util.List;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.vms.VmSimple;

/** Reproduces off-during-migration and verifies the narrow destination guard. */
public final class MigrationPowerRegression {
    private static Host host() {
        List<Pe> pes = new ArrayList<>();
        for (int i=0;i<8;i++) pes.add(new PeSimple(1000));
        var h = new ConsolidationExperiment.MigrationAwareHost(16384,10000,1000000,pes);
        h.setRamProvisioner(new HostLocalResourceProvisioner(h.getRamProvisioner().getPmResource()));
        h.setBwProvisioner(new HostLocalResourceProvisioner(h.getBwProvisioner().getPmResource()));
        h.setVmScheduler(new VmSchedulerTimeShared());
        h.setIdleShutdownDeadline(0);
        return h;
    }

    private static void check(boolean guard) {
        System.setProperty("coe619.powerGuard", Boolean.toString(guard));
        var simulation = new CloudSimPlus();
        Host source = host(), target = host();
        new DatacenterSimple(simulation,List.of(source,target));
        var vm = new VmSimple(0,1000,2).setRam(2048).setBw(1000).setSize(10000);
        if (!source.createVm(vm).fully() || !target.addMigratingInVm(vm)) throw new AssertionError("Migration fixture failed");
        target.updateProcessing(0);
        if (target.isActive() != guard) throw new AssertionError("Unexpected destination state during transfer");
        target.removeMigratingInVm(vm);
        target.updateProcessing(300);
        if (target.isActive()) throw new AssertionError("A truly empty host should still shut down");
        if (target.getIdleShutdownDeadline()!=0) throw new AssertionError("Deadline must be restored");
        System.out.println("PASS: guard="+guard+"; incoming transfer protected="+guard+"; genuine empty shutdown retained");
    }

    public static void main(String[] args) {
        String prior = System.getProperty("coe619.powerGuard");
        try { check(false); check(true); }
        finally { if(prior==null)System.clearProperty("coe619.powerGuard");else System.setProperty("coe619.powerGuard",prior); }
    }
}
