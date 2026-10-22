package kfupm.coe619;

import org.cloudsimplus.provisioners.ResourceProvisionerSimple;
import org.cloudsimplus.resources.ResourceManageable;
import org.cloudsimplus.vms.Vm;

import java.util.IdentityHashMap;
import java.util.Map;

final class HostLocalResourceProvisioner extends ResourceProvisionerSimple {
    private final Map<Vm, Long> allocations = new IdentityHashMap<>();

    HostLocalResourceProvisioner(final ResourceManageable hostResource) {
        super();
        setResources(hostResource, vm -> ResourceManageable.NULL);
    }

    // A migrating VM is temporarily allocated on two hosts, so reservations must be host-local.
    @Override
    public boolean allocateResourceForVm(final Vm vm, final long newTotalVmResourceCapacity) {
        if (newTotalVmResourceCapacity < 0) {
            return false;
        }

        final long current = getAllocatedResourceForVm(vm);
        final long difference = newTotalVmResourceCapacity - current;
        if (difference > 0 && !getPmResource().allocateResource(difference)) {
            return false;
        }
        if (difference < 0 && !getPmResource().deallocateResource(-difference)) {
            return false;
        }

        if (newTotalVmResourceCapacity == 0) {
            allocations.remove(vm);
        } else {
            allocations.put(vm, newTotalVmResourceCapacity);
        }
        return true;
    }

    @Override
    public long getAllocatedResourceForVm(final Vm vm) {
        return allocations.getOrDefault(vm, 0L);
    }

    @Override
    public long deallocateResourceForVm(final Vm vm) {
        final long allocation = allocations.getOrDefault(vm, 0L);
        if (allocation == 0) {
            return 0;
        }

        if (!getPmResource().deallocateResource(allocation)) {
            throw new IllegalStateException("Could not release host resource allocation for " + vm);
        }
        allocations.remove(vm);
        return allocation;
    }

    @Override
    public boolean isSuitableForVm(final Vm vm, final long newVmTotalAllocatedResource) {
        if (newVmTotalAllocatedResource < 0) {
            return false;
        }
        final long current = getAllocatedResourceForVm(vm);
        return newVmTotalAllocatedResource <= current
            || getAvailableResource() >= newVmTotalAllocatedResource - current;
    }
}
