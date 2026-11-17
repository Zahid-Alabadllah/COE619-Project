package kfupm.coe619;

import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostStateHistoryEntry;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
    private final Map<Long, Double> historyCutoff = new HashMap<>();
    private final Map<Long, Long> lastWarningBucket = new HashMap<>();
    private final Map<Long, Integer> warningStreak = new HashMap<>();
    private record Pending(double target, double estimate, double naive, long generation) { }
    private final Map<Long, Long> generation = new HashMap<>();
    private final Map<Long, List<Pending>> pending = new HashMap<>();
    private long predictionCount;

    /** Experimental: discard pre-migration history only when explicitly enabled. */
    public void resetAfterMigration(final Host host, final double time) {
        generation.merge(host.getId(), 1L, Long::sum);
        if (Boolean.getBoolean("coe619.resetHistory")) {
            historyCutoff.put(host.getId(), time);
            warningStreak.remove(host.getId());
            lastWarningBucket.remove(host.getId());
        }
    }

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
    public double getOverUtilizationThreshold(final Host host) {
        if (!Boolean.getBoolean("coe619.adaptiveThreshold")) return super.getOverUtilizationThreshold(host);
        final org.apache.commons.math3.stat.descriptive.DescriptiveStatistics stats =
            new org.apache.commons.math3.stat.descriptive.DescriptiveStatistics();
        for (final HostStateHistoryEntry entry : host.getStateHistory()) {
            if (entry.active()) stats.addValue(HostCpuUtilization.fractionOfCapacity(host, entry));
        }
        if (stats.getN() < 2) return super.getOverUtilizationThreshold(host);
        return 1 - 0.75 * (stats.getPercentile(75) - stats.getPercentile(25));
    }

    @Override
    public boolean isOverloaded(final Host host) {
        if (Boolean.getBoolean("coe619.evaluateForecast")) evaluatePending(host);
        final boolean reactive = super.isOverloaded(host);
        if (reactive) {
            return true;
        }

        if (Boolean.getBoolean("coe619.disableForecast")) return false;
        final List<double[]> samples = recentActiveSamples(host);
        if (samples.size() < windowSize) {
            return false; // not enough history: behave like THR
        }

        predictionCount++;
        final double now = host.getSimulation().clock();
        final double rawForecast = forecast(samples, now + horizonSeconds);
        // Experimental damped trend: setting selected on early real_a only.
        final double forecast = Boolean.getBoolean("coe619.dampedForecast")
            ? samples.get(0)[1] + 0.5 * (rawForecast - samples.get(0)[1]) : rawForecast;
        if (Boolean.getBoolean("coe619.evaluateForecast")) {
            final List<Pending> queue = pending.computeIfAbsent(host.getId(), id -> new ArrayList<>());
            final double target = now + horizonSeconds;
            if (queue.stream().noneMatch(item -> Math.abs(item.target() - target) < 1e-6)) {
                queue.add(new Pending(target, forecast, samples.get(0)[1], generation.getOrDefault(host.getId(), 0L)));
                final String input = samples.stream().map(sample -> sample[0] + ":" + sample[1])
                    .collect(java.util.stream.Collectors.joining(";"));
                System.out.printf(java.util.Locale.ROOT, "FORECAST_INPUT host=%d target=%.3f samples=%s%n",
                    host.getId(), target, input);
            }
        }
        final boolean rawWarning = forecast > getOverUtilizationThreshold(host);
        boolean predicted = rawWarning;
        if (Boolean.getBoolean("coe619.confirmWarning")) {
            final long bucket = (long) Math.floor(now / horizonSeconds);
            final long previous = lastWarningBucket.getOrDefault(host.getId(), Long.MIN_VALUE);
            if (!rawWarning) {
                warningStreak.put(host.getId(), 0);
            } else if (bucket != previous) {
                final int count = previous == bucket - 1 ? warningStreak.getOrDefault(host.getId(), 0) + 1 : 1;
                warningStreak.put(host.getId(), count);
            }
            lastWarningBucket.put(host.getId(), bucket);
            predicted = rawWarning && warningStreak.getOrDefault(host.getId(), 0) >= 2;
        }
        if (predicted) {
            predictiveTriggerCount++;
        }
        // Opt-in investigation only: observe forecasts without changing decisions.
        if (Boolean.getBoolean("coe619.predictorDiagnostics") && (predictionCount <= 12 || predicted)) {
            final double oldest = samples.stream().mapToDouble(sample -> sample[0]).min().orElse(now);
            final double newest = samples.stream().mapToDouble(sample -> sample[0]).max().orElse(now);
            System.out.printf(java.util.Locale.ROOT,
                "PREDICTOR_CHECK time=%.3f host=%d vms=%d oldest=%.3f newest=%.3f span=%.3f forecast=%.6f flag=%s%n",
                now, host.getId(), host.getVmList().size(), oldest, newest, newest - oldest, forecast, predicted);
        }
        return predicted;
    }

    private void evaluatePending(final Host host) {
        final List<Pending> queue = pending.get(host.getId());
        if (queue == null) return;
        final double now = host.getSimulation().clock();
        final var iterator = queue.iterator();
        while (iterator.hasNext()) {
            final Pending item = iterator.next();
            if (now < item.target()) continue;
            if (item.generation() == generation.getOrDefault(host.getId(), 0L)) {
                for (final HostStateHistoryEntry entry : host.getStateHistory()) {
                    if (entry.active() && Math.abs(entry.time() - item.target()) <= 1) {
                        final double observed = HostCpuUtilization.fractionOfCapacity(host, entry);
                        System.out.printf(java.util.Locale.ROOT,
                            "FORECAST_ERROR host=%d target=%.3f actual=%.6f forecast=%.6f persistence=%.6f%n",
                            host.getId(), item.target(), observed, item.estimate(), item.naive());
                        iterator.remove();
                        break;
                    }
                }
            }
            if (now > item.target() + 1 && queue.contains(item)) iterator.remove();
        }
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
            if (entry.active() && entry.time() >= historyCutoff.getOrDefault(host.getId(), Double.NEGATIVE_INFINITY)) {
                // Experimental ablation only: prevent closely spaced state events from
                // dominating the window. Actual timestamps still drive the OLS fit.
                final boolean spaced = Boolean.getBoolean("coe619.spacedHistory");
                if (spaced && !samples.isEmpty()
                    && samples.get(samples.size() - 1)[0] - entry.time() < 300 - 1e-6) {
                    continue;
                }
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
