package kfupm.coe619;

import java.util.*;
import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationAbstract;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;

/** Research-only numeric reservations: never create or destroy a physical VM while planning. */
public final class UnderloadReservationPlanner {
    private UnderloadReservationPlanner() { }
    private static final class Room {
        double reservedCpu, allocatedCpu;
        long ram, bw, storage;
        Room(Host host) {
            reservedCpu = host.getVmList().stream().mapToDouble(Vm::getTotalCpuMipsRequested).sum();
            allocatedCpu = host.getVmList().stream().mapToDouble(host::getTotalAllocatedMipsForVm).sum();
            ram = host.getRam().getAvailableResource();
            bw = host.getBw().getAvailableResource();
            storage = host.getStorage().getAvailableResource();
        }
        Room(Room original) {
            reservedCpu=original.reservedCpu; allocatedCpu=original.allocatedCpu;
            ram=original.ram; bw=original.bw; storage=original.storage;
        }
        void reserve(Vm vm) {
            reservedCpu += vm.getTotalCpuMipsRequested();
            allocatedCpu += vm.getTotalCpuMipsRequested();
            ram -= vm.getRam().getCapacity(); bw -= vm.getBw().getCapacity();
            storage -= vm.getStorage().getCapacity();
        }
    }
    private static boolean transferring(Host host) {
        return !host.getVmsMigratingIn().isEmpty() || !host.getVmsMigratingOut().isEmpty();
    }
    /** Returns whether an eligible underloaded source was found. Map updates are atomic on success. */
    public static boolean plan(VmAllocationPolicyMigrationAbstract policy, Set<Host> overloaded,
                               Map<Vm, Host> existing) {
        validatePlacement(policy);
        boolean audit=Boolean.getBoolean("coe619.auditPlanning");
        String before=audit?physicalState(policy):null;
        try { return planInternal(policy,overloaded,existing); }
        finally {
            if(audit) {
                if(!before.equals(physicalState(policy))) throw new IllegalStateException("Underload planning changed physical state");
                System.out.printf(java.util.Locale.ROOT,"PLAN_STATE_AUDIT time=%.6f unchanged=true%n",policy.getDatacenter().getSimulation().clock());
            }
        }
    }
    private static void validatePlacement(VmAllocationPolicyMigrationAbstract policy) {
        Map<Vm,Integer> counts=new IdentityHashMap<>();
        for(Host host:policy.getDatacenter().getHostList()) {
            Set<Vm> unique=Collections.newSetFromMap(new IdentityHashMap<>());
            for(Vm vm:host.getVmList()) {
                if(!unique.add(vm)) throw new IllegalStateException("Duplicate VM within physical host before planning");
                counts.merge(vm,1,Integer::sum);
                if(vm.getHost()!=host && !host.getVmsMigratingIn().contains(vm))
                    throw new IllegalStateException("VM belongs to a different physical host before planning");
            }
        }
        for(var entry:counts.entrySet()) {
            if(entry.getValue()>1 && !entry.getKey().isInMigration())
                throw new IllegalStateException("VM appears on multiple hosts without migration before planning");
        }
    }
    private static String physicalState(VmAllocationPolicyMigrationAbstract policy) {
        StringBuilder s=new StringBuilder();
        Set<Vm> all=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Host host:policy.getDatacenter().getHostList()) {
            s.append("H").append(host.getId()).append(':').append(host.isActive()).append(':')
             .append(host.getRam().getAvailableResource()).append(':').append(host.getBw().getAvailableResource())
             .append(':').append(host.getStorage().getAvailableResource());
            for(Vm vm:host.getVmList()) {s.append("V").append(System.identityHashCode(vm));all.add(vm);}
            for(Vm vm:host.getVmsMigratingIn()) {s.append("I").append(System.identityHashCode(vm));all.add(vm);}
            for(Vm vm:host.getVmsMigratingOut()) {s.append("O").append(System.identityHashCode(vm));all.add(vm);}
            for(var pe:host.getPeList())s.append("PE").append(pe.getStatus()).append(':').append(pe.getPeProvisioner().getAvailableResource());
        }
        // Identity iteration order is stable while this set is not mutated, but rebuilding it need not be.
        List<Vm> ordered=new ArrayList<>(all);ordered.sort(Comparator.comparingLong(Vm::getId));
        for(Vm value:ordered) {
            var vm=(org.cloudsimplus.vms.VmSimple)value;
            s.append("VM").append(vm.getId()).append(':').append(vm.getHost().getId()).append(':')
             .append(vm.isCreated()).append(':').append(vm.isInMigration()).append(':').append(vm.getStartTime())
             .append(':').append(vm.getRequestedMips().pes()).append(':').append(vm.getRequestedMips().totalMips())
             .append(':').append(vm.getAllocatedMips().pes()).append(':').append(vm.getAllocatedMips().totalMips())
             .append(':').append(vm.getBroker().getVmExecList().contains(vm));
        }
        return s.toString();
    }
    private static boolean planInternal(VmAllocationPolicyMigrationAbstract policy, Set<Host> overloaded,
                               Map<Vm, Host> existing) {
        List<Host> hosts = new ArrayList<>(policy.getDatacenter().getHostList());
        hosts.sort(Comparator.comparingLong(Host::getId));
        Map<Host, Room> rooms = new IdentityHashMap<>();
        for (Host host : hosts) rooms.put(host, new Room(host));
        Set<Host> ignoredSources = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Host> ignoredTargets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Host host : hosts) {
            if (!host.isActive() || host.isFailed() || transferring(host) || overloaded.contains(host)) {
                ignoredSources.add(host); ignoredTargets.add(host);
            }
        }
        for (Map.Entry<Vm, Host> entry : existing.entrySet()) {
            ignoredSources.add(entry.getValue());
            Room room=rooms.get(entry.getValue());
            if (room == null) throw new IllegalArgumentException("Cross-datacenter planning is unsupported");
            if (!entry.getValue().getVmList().contains(entry.getKey())) room.reserve(entry.getKey());
        }
        Map<Vm, Host> staged = new LinkedHashMap<>();
        boolean found = false;
        while (true) {
            Host source = hosts.stream()
                .filter(h -> !ignoredSources.contains(h) && !h.getVmList().isEmpty())
                .filter(h -> rooms.get(h).reservedCpu / h.getTotalMipsCapacity() < policy.getUnderUtilizationThreshold())
                .min(Comparator.comparingDouble(Host::getCpuPercentUtilization).thenComparingLong(Host::getId))
                .orElse(Host.NULL);
            if (source == Host.NULL) break;
            found = true; ignoredSources.add(source); ignoredTargets.add(source);
            List<Vm> candidates = new ArrayList<>(source.getMigratableVms());
            if (candidates.size() != source.getVmList().size()) continue;
            candidates.sort(Comparator.comparingDouble((Vm vm) -> vm.getTotalCpuMipsUtilization(source.getSimulation().clock()))
                .reversed().thenComparingLong(Vm::getId));
            Map<Host, Room> trial = new IdentityHashMap<>();
            for (Host host : hosts) trial.put(host, new Room(rooms.get(host)));
            Map<Vm, Host> proposal = new LinkedHashMap<>();
            boolean fits = true;
            for (Vm vm : candidates) {
                Host best = Host.NULL;
                double bestCost = Double.POSITIVE_INFINITY;
                for (Host target : hosts) {
                    Room room = trial.get(target);
                    if (ignoredTargets.contains(target) || target == vm.getHost() || !target.isSuitableForVm(vm)) continue;
                    double capacity = target.getTotalMipsCapacity();
                    if (room.reservedCpu / capacity < policy.getUnderUtilizationThreshold()) continue;
                    double cpu = vm.getTotalCpuMipsRequested();
                    if (room.reservedCpu + cpu > capacity + 1e-9 || room.ram < vm.getRam().getCapacity()
                        || room.bw < vm.getBw().getCapacity() || room.storage < vm.getStorage().getCapacity()) continue;
                    if ((room.reservedCpu + cpu) / capacity > policy.getOverUtilizationThreshold(target)) continue;
                    double cost = target.getPowerModel().getPower((room.allocatedCpu + cpu) / capacity)
                        - target.getPowerModel().getPower();
                    if (cost < bestCost) { bestCost=cost; best=target; }
                }
                if (best == Host.NULL) { fits=false; break; }
                proposal.put(vm, best); trial.get(best).reserve(vm);
            }
            if (fits && !proposal.isEmpty()) {
                rooms.clear(); rooms.putAll(trial); staged.putAll(proposal); ignoredSources.addAll(proposal.values());
            }
        }
        existing.putAll(staged);
        return found;
    }
}
