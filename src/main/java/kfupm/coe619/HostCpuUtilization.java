package kfupm.coe619;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostStateHistoryEntry;

final class HostCpuUtilization {
    private HostCpuUtilization() { }

    static double fractionOfCapacity(final Host host, final HostStateHistoryEntry entry) {
        final double totalMipsCapacity = host.getTotalMipsCapacity();
        if (totalMipsCapacity <= 0) {
            throw new IllegalStateException("Host has no working CPU capacity: " + host);
        }
        return entry.allocatedMips() / totalMipsCapacity;
    }
}
