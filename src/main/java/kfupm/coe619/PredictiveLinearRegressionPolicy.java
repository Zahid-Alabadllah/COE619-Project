package kfupm.coe619;

import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostStateHistoryEntry;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * Predictive overload detection using ordinary least-squares (OLS) linear regression.
 *
 * <p>For each host, a straight line u(t) = a + b*t is fitted to the last {@code windowSize}
 * active CPU-utilization samples (allocated MIPS / physical host MIPS capacity). The host is
 * treated as overloaded if either its current utilization or the utilization forecast one
 * {@code horizonSeconds} ahead exceeds the upper threshold. This lets the policy migrate VMs
 * before a rising trend crosses the threshold, instead of only after.</p>
 *
 * <p>With fewer than {@code windowSize} samples the prediction is not attempted and the
 * policy behaves exactly like the static threshold (THR) policy it extends.</p>
 *
 * <p>Design notes and limits:
 * <ul>
 *   <li>The threshold and VM selection (MMT) are the same as THR, so any difference against
 *       THR comes from the forecast term only.</li>
 *   <li>This is a lightweight trend extrapolation, not the Local Regression (LOESS) method of
 *       Beloglazov and Buyya (2012). It makes no claim of superior accuracy.</li>
 *   <li>CloudSim Plus also calls {@link #isOverloaded(Host)} in some destination-host checks,
 *       so the forecast can also make a rising host less attractive as a migration target.</li>
 * </ul>
 */
public class PredictiveLinearRegressionPolicy extends VmAllocationPolicyMigrationStaticThreshold {
    private final int windowSize;
    private final double horizonSeconds;
    private long predictionCount;
    private long predictiveTriggerCount;

    public PredictiveLinearRegressionPolicy(
        final VmSelectionPolicy selectionPolicy,
        final double upperThreshold,
        final int windowSize,
        final double horizonSeconds) {
        super(selectionPolicy, upperThreshold);
        if (windowSize < 2) {
            throw new IllegalArgumentException("windowSize must be at least 2");
        }
        if (horizonSeconds <= 0) {
            throw new IllegalArgumentException("horizonSeconds must be positive");
        }
        this.windowSize = windowSize;
        this.horizonSeconds = horizonSeconds;
    }

    @Override
    public boolean isOverloaded(final Host host) {
        final boolean reactive = super.isOverloaded(host);
        if (reactive) {
            return true;
        }

        final List<double[]> samples = recentActiveSamples(host);
        if (samples.size() < windowSize) {
            return false; // not enough history: behave like THR
        }

        predictionCount++;
        final double now = host.getSimulation().clock();
        final double forecast = forecast(samples, now + horizonSeconds);
        final boolean predicted = forecast > getOverUtilizationThreshold(host);
        if (predicted) {
            predictiveTriggerCount++;
        }
        return predicted;
    }

    /** Number of times a forecast was computed. */
    public long getPredictionCount() {
        return predictionCount;
    }

    /** Number of times the forecast (not the current utilization) flagged a host as overloaded. */
    public long getPredictiveTriggerCount() {
        return predictiveTriggerCount;
    }

    private List<double[]> recentActiveSamples(final Host host) {
        final List<HostStateHistoryEntry> history = host.getStateHistory();
        final List<double[]> samples = new ArrayList<>(windowSize);
        for (int i = history.size() - 1; i >= 0 && samples.size() < windowSize; i--) {
            final HostStateHistoryEntry entry = history.get(i);
            if (entry.active()) {
                samples.add(new double[]{entry.time(), HostCpuUtilization.fractionOfCapacity(host, entry)});
            }
        }
        return samples;
    }

    /**
     * OLS fit of utilization against time, evaluated at {@code targetTime}.
     * Falls back to the sample mean if all samples share the same timestamp.
     */
    static double forecast(final List<double[]> samples, final double targetTime) {
        final int n = samples.size();
        double meanT = 0;
        double meanU = 0;
        for (final double[] s : samples) {
            meanT += s[0];
            meanU += s[1];
        }
        meanT /= n;
        meanU /= n;

        double sxx = 0;
        double sxy = 0;
        for (final double[] s : samples) {
            final double dt = s[0] - meanT;
            sxx += dt * dt;
            sxy += dt * (s[1] - meanU);
        }
        if (sxx == 0) {
            return meanU;
        }
        final double slope = sxy / sxx;
        return meanU + slope * (targetTime - meanT);
    }
}
