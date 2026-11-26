package kfupm.coe619;

import java.util.*;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.*;
import org.cloudsimplus.resources.*;
import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
import org.cloudsimplus.vms.*;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicyMinimumMigrationTime;
import org.cloudsimplus.power.models.PowerModelHostSimple;

/** Real state, resource rollback, threshold and public-dispatch regressions. */
public final class UnderloadReservationPlannerCheck {
    record Fixture(List<Host> hosts, List<Vm> vms, VmAllocationPolicyMigrationStaticThreshold policy) { }
    static Fixture fixture(int[] counts, int cores, long ram, long bw, long storage, double lower, double upper, boolean throwing) {
        var simulation = new CloudSimPlus();
        List<Host> hosts = new ArrayList<>(); List<Vm> vms = new ArrayList<>();
        for (int n : counts) {
            List<Pe> pes = new ArrayList<>(); for (int i=0;i<cores;i++) pes.add(new PeSimple(1000));
            Host host = new HostSimple(ram,bw,storage,pes);
            host.setRamProvisioner(new HostLocalResourceProvisioner(host.getRamProvisioner().getPmResource()));
            host.setBwProvisioner(new HostLocalResourceProvisioner(host.getBwProvisioner().getPmResource()));
            host.setVmScheduler(new VmSchedulerTimeShared()); host.setPowerModel(new PowerModelHostSimple(250,175));
            hosts.add(host);
        }
        var dc = new DatacenterSimple(simulation,hosts);
        var policy = new VmAllocationPolicyMigrationStaticThreshold(new VmSelectionPolicyMinimumMigrationTime(), Math.min(.99,upper)) {
            int calls;
            @Override public double getOverUtilizationThreshold(Host host) {
                if (throwing && ++calls==2) throw new IllegalStateException("injected threshold failure");
                // A dynamic threshold may equal1; the static policy constructor itself disallows it.
                return upper;
            }
        };
        policy.setUnderUtilizationThreshold(lower); dc.setVmAllocationPolicy(policy);
        for (int h=0;h<counts.length;h++) for (int i=0;i<counts[h];i++) {
            Vm vm = new VmSimple(vms.size(),1000,2).setRam(2048).setBw(1000).setSize(10000);
            if (!hosts.get(h).createVm(vm).fully()) throw new AssertionError("Fixture admission failed");
            vms.add(vm);
        }
        return new Fixture(hosts,vms,policy);
    }
    static Fixture fixture(int... counts) {return fixture(counts,8,16384,10000,1000000,.35,.8,false);}
    static String snapshot(Fixture f) {
        StringBuilder s=new StringBuilder();
        for (Host host:f.hosts()) {
            s.append("H").append(host.getId()).append(':').append(host.isActive()).append(':')
             .append(host.getRam().getAvailableResource()).append(':').append(host.getBw().getAvailableResource())
             .append(':').append(host.getStorage().getAvailableResource());
            for(Vm vm:host.getVmList())s.append("V").append(System.identityHashCode(vm));
            for(Vm vm:host.getVmsMigratingIn())s.append("I").append(System.identityHashCode(vm));
            for(Vm vm:host.getVmsMigratingOut())s.append("O").append(System.identityHashCode(vm));
            for(Pe pe:host.getPeList()) {
                s.append("PE").append(pe.getStatus()).append(':').append(pe.getPeProvisioner().getAvailableResource());
                for(Vm vm:f.vms())s.append(':').append(pe.getPeProvisioner().getAllocatedResourceForVm(vm));
            }
            for(Vm vm:f.vms())s.append("R").append(host.getRamProvisioner().getAllocatedResourceForVm(vm))
                .append(':').append(host.getBwProvisioner().getAllocatedResourceForVm(vm));
        }
        for(Vm value:f.vms()) {
            VmSimple vm=(VmSimple)value;
            s.append("VM").append(vm.getId()).append(':').append(vm.getHost().getId()).append(':')
             .append(vm.isCreated()).append(':').append(vm.isInMigration()).append(':').append(vm.getStartTime())
             .append(':').append(vm.getRequestedMips().pes()).append(':').append(vm.getRequestedMips().totalMips())
             .append(':').append(vm.getAllocatedMips().pes()).append(':').append(vm.getAllocatedMips().totalMips())
             .append(':').append(vm.getProcessor().getAllocatedResource()).append(':').append(vm.getBroker().getVmExecList().contains(vm));
        }
        return s.toString();
    }
    static Map<Vm,Host> checked(Fixture f, Set<Host> overloaded, Map<Vm,Host> map) {
        String before=snapshot(f); UnderloadReservationPlanner.plan(f.policy(),overloaded,map);
        if(!before.equals(snapshot(f))) throw new AssertionError("Planning changed physical state");
        return map;
    }
    static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) {
        Fixture f=fixture(1,2);
        var first=checked(f,Set.of(),new LinkedHashMap<>());
        require(first.size()==1 && first.get(f.vms().get(0))==f.hosts().get(1),"Expected one planned move");
        require(first.equals(checked(f,Set.of(),new LinkedHashMap<>())) ,"Planning is not repeatable");
        System.out.println("PASS: successful planning leaves residency, resources, PE state, VM state and lifetime unchanged; repeatable.");
        f=fixture(1,3);require(checked(f,Set.of(),new LinkedHashMap<>()).isEmpty(),"80% reserved limit must reject a fourth VM");
        f=fixture(new int[]{1,2},8,16384,10000,1000000,.35,.75,false);
        require(checked(f,Set.of(),new LinkedHashMap<>()).size()==1,"Exactly equal upper threshold must be accepted");
        System.out.println("PASS: reserved CPU and exact threshold boundary match library semantics.");
        f=fixture(new int[]{2,3},8,16384,10000,1000000,.6,1,false);
        require(checked(f,Set.of(),new LinkedHashMap<>()).isEmpty(),"Partial source evacuation must roll back at physical CPU limit");
        for(int resource=0;resource<3;resource++) {
            f=fixture(new int[]{3,5},16,resource==0?14000:32768,resource==1?6500:20000,resource==2?65000:1000000,.6,1,false);
            require(checked(f,Set.of(),new LinkedHashMap<>()).isEmpty(),"Partial resource evacuation not rolled back: "+resource);
        }
        System.out.println("PASS: incomplete source evacuation is atomic for CPU, RAM, bandwidth and storage.");
        f=fixture(1,2);require(checked(f,Set.of(f.hosts().get(1)),new LinkedHashMap<>()).isEmpty(),"Excluded destination used");
        Vm incoming = new VmSimple(99,1000,2).setRam(2048).setBw(1000).setSize(10000);
        f.hosts().get(1).addMigratingInVm(incoming);
        require(checked(f,Set.of(),new LinkedHashMap<>()).isEmpty(),"In-flight host should be skipped");
        System.out.println("PASS: excluded targets and existing incoming migrations are left alone.");
        f=fixture(1,2,1);Map<Vm,Host> prior=new LinkedHashMap<>();prior.put(f.vms().get(3),f.hosts().get(1));
        require(checked(f,Set.of(f.hosts().get(2)),prior).size()==1,"Existing overload reservations must consume capacity");
        f=fixture(new int[]{1,2,1},8,16384,10000,1000000,.35,1,false);
        require(checked(f,Set.of(),new LinkedHashMap<>()).size()==2,"Expected atomic multi-source planning");
        System.out.println("PASS: shared reservations account for prior overload plans and multiple source groups.");
        f=fixture(new int[]{1,2,1},8,16384,10000,1000000,.35,1,true);
        String before=snapshot(f);Map<Vm,Host> map=new LinkedHashMap<>();boolean threw=false;
        try { UnderloadReservationPlanner.plan(f.policy(),Set.of(),map); }
        catch(IllegalStateException ex) {require(ex.getMessage().equals("injected threshold failure"),"Unexpected failure");threw=true;}
        require(threw && map.isEmpty() && before.equals(snapshot(f)),"Exception changed physical state or partially committed map");
        System.out.println("PASS: injected exception after a staged group leaves state and output map unchanged.");
        f=fixture(1,2);f.hosts().get(0).getVmList().add(f.vms().get(0));
        before=snapshot(f);map=new LinkedHashMap<>();threw=false;
        try {UnderloadReservationPlanner.plan(f.policy(),Set.of(),map);}
        catch(IllegalStateException ex) {require(ex.getMessage().contains("Duplicate VM"),"Wrong invalid-state failure");threw=true;}
        require(threw && map.isEmpty() && before.equals(snapshot(f)),"Invalid input should be rejected without modification");
        System.out.println("PASS: already-invalid duplicate residency is rejected before planning.");
        if(args.length>0 && args[0].equals("public")) {
            f=fixture(1,2);before=snapshot(f);map=f.policy().getOptimizedAllocationMap(f.vms());
            require(map.size()==1 && before.equals(snapshot(f)) && f.policy().isUnderloaded(),"Patched public dispatch failed");
            System.out.println("PASS: public optimizer returns a move without physical mutation and reports underload status.");
        }
    }
}
