package kfupm.coe619;

import org.cloudsimplus.allocationpolicies.migration.VmAllocationPolicyMigrationStaticThreshold;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostStateHistoryEntry;
import org.cloudsimplus.selectionpolicies.VmSelectionPolicy;
import org.cloudsimplus.vms.Vm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;

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
 * <p>Opt-in research mode can select the adaptive trend or a recent mean using only
 * earlier verified forecast errors. The default remains the original OLS policy.</p>
 *
 * <p>Design notes and limits:
 * <ul>
 *   <li>The threshold and VM selection (MMT) are the same as THR, so any difference against
 *       THR comes from the forecast term only.</li>
 *   <li>This is a lightweight trend extrapolation, not the Local Regression (LOESS) method of
 *       Beloglazov and Buyya (2012). It makes no claim of superior accuracy.</li>
 *   <li>CloudSim Plus 8.0.0 calls {@link #isOverloaded(Host)} during source detection and
 *       again after temporary VM removal while planning an evacuation. Destination
 *       placement uses the framework's private threshold check, not this override.</li>
 * </ul>
 */
public class PredictiveLinearRegressionPolicy extends VmAllocationPolicyMigrationStaticThreshold {
    private final int windowSize;
    private final double horizonSeconds;
    private final Map<Long, Double> historyCutoff = new HashMap<>();
    private final Map<Long, Long> lastWarningBucket = new HashMap<>();
    private final Map<Long, Integer> warningStreak = new HashMap<>();
    private record Pending(double target, double estimate, double naive, double raw,
                           double trendEstimate, double meanEstimate, double robustEstimate, long generation) { }
    private final Map<Long, RobustPairs> robustPairs = new HashMap<>();
    private final Map<Long, Long> generation = new HashMap<>();
    private final Map<Long, List<Pending>> pending = new HashMap<>();
    private final Map<Long, OnlineBlend> onlineBlends = new HashMap<>();
    private final Map<Long, List<? extends Vm>> originalPlanningVms = new HashMap<>();
    private long predictionCount;

    /**
     * Opt-in Theil-Sen current-to-next fit; bounds and fallback are project choices.
     * Median-slope/joint-intercept reference: https://docs.scipy.org/doc/scipy/reference/generated/scipy.stats.theilslopes.html
     * This is an experimental CPU predictor, not a demonstrated energy-saving policy.
     */
    static final class RobustPairs {
        private record Observation(double target, double latest, double actual, double estimate) { }
        private final Deque<Observation> ready = new ArrayDeque<>();
        private final Deque<Observation> history = new ArrayDeque<>();

        void observe(double target, double latest, double actual, double estimate) {
            ready.addLast(new Observation(target, latest, actual, estimate));
        }

        private static double median(List<Double> values) {
            values.sort(Double::compare);
            int n = values.size();
            return n % 2 == 1 ? values.get(n / 2) : (values.get(n / 2 - 1) + values.get(n / 2)) / 2;
        }

        double[] predict(double issue, double latest) {
            var iterator = ready.iterator();
            while (iterator.hasNext()) {
                var item = iterator.next();
                if (item.target() >= issue - 1e-6) continue;
                history.addLast(item);
                if (history.size() > 20) history.removeFirst();
                iterator.remove();
            }
            if (history.size() < 10) return new double[]{latest, latest};
            var points = new ArrayList<>(history);
            var slopes = new ArrayList<Double>();
            for (int i = 0; i < points.size(); i++) {
                for (int j = i + 1; j < points.size(); j++) {
                    double dx = points.get(j).latest() - points.get(i).latest();
                    if (Math.abs(dx) > 1e-12) slopes.add((points.get(j).actual() - points.get(i).actual()) / dx);
                }
            }
            double slope = slopes.isEmpty() ? 0 : Math.max(0, Math.min(1, median(slopes)));
            var intercepts = new ArrayList<Double>();
            double baselineError = 0, robustError = 0;
            for (var item : history) {
                intercepts.add(item.actual() - slope * item.latest());
                baselineError += Math.abs(item.latest() - item.actual());
                robustError += Math.abs(item.estimate() - item.actual());
            }
            double estimate = Math.max(0, Math.min(1, median(intercepts) + slope * latest));
            return new double[]{estimate, robustError < baselineError ? estimate : latest};
        }
    }

    /** Research-only learner: use resolved past errors to choose how much trend to trust. */
    static final class OnlineBlend {
        private record Error(double target, double trend, double change, double actual,
                             double trendEstimate, double meanEstimate) { }
        private final Deque<Error> ready = new ArrayDeque<>();
        private final Deque<Error> history = new ArrayDeque<>();

        void observe(final double target, final double raw, final double latest, final double actual) {
            observe(target, raw, latest, actual, Double.NaN, Double.NaN);
        }

        void observe(final double target, final double raw, final double latest, final double actual,
                     final double trendEstimate, final double meanEstimate) {
            ready.addLast(new Error(target, raw - latest, actual - latest, actual, trendEstimate, meanEstimate));
        }

        private void update(final double issueTime) {
            final var iterator = ready.iterator();
            while (iterator.hasNext()) {
                final Error error = iterator.next();
                // Even a target observed at this exact instant waits for the next forecast.
                if (error.target() >= issueTime - 1e-6) continue;
                history.addLast(error);
                if (history.size() > 20) history.removeFirst();
                iterator.remove();
            }
        }

        double weight(final double issueTime) {
            update(issueTime);
            if (history.size() < 10) return 0.5;
            double denominator = 0;
            double numerator = 0;
            for (final Error error : history) {
                denominator += error.trend() * error.trend();
                numerator += error.trend() * error.change();
            }
            return denominator > 1e-12 ? Math.max(0, Math.min(1, numerator / denominator)) : 0.5;
        }

        double selectForecast(final double issueTime, final double trend, final double mean) {
            update(issueTime);
            double trendError = 0;
            double meanError = 0;
            int count = 0;
            for (final Error error : history) {
                if (!Double.isFinite(error.trendEstimate()) || !Double.isFinite(error.meanEstimate())) continue;
                trendError += Math.abs(error.trendEstimate() - error.actual());
                meanError += Math.abs(error.meanEstimate() - error.actual());
                count++;
            }
            return count >= 10 && meanError < trendError ? mean : trend;
        }
    }

    /** Experimental: discard pre-migration history only when explicitly enabled. */
    public void resetAfterMigration(final Host host, final double time) {
        generation.merge(host.getId(), 1L, Long::sum);
        robustPairs.remove(host.getId());
        if (Boolean.getBoolean("coe619.robustForecast") && Boolean.getBoolean("coe619.evaluateForecast")) {
            System.out.printf(java.util.Locale.ROOT, "FORECAST_ROBUST_RESET host=%d time=%.3f%n", host.getId(), time);
        }
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
    public Map<Vm, Host> getOptimizedAllocationMap(final List<? extends Vm> vmList) {
        if (!Boolean.getBoolean("coe619.planningHistoryGuard")) return super.getOptimizedAllocationMap(vmList);
        for (final Host host : getHostList()) {
            originalPlanningVms.put(host.getId(), List.copyOf(host.getVmList()));
        }
        try {
            return super.getOptimizedAllocationMap(vmList);
        } finally {
            originalPlanningVms.clear();
        }
    }

    @Override
    public boolean isOverloaded(final Host host) {
        final List<? extends Vm> original = originalPlanningVms.get(host.getId());
        if (original != null && (host.getVmList().size() != original.size() || !host.getVmList().containsAll(original))) {
            // Past aggregate CPU describes the original VM group, not this temporary
            // planning subset. Finish VM selection using current CPU only.
            if (Boolean.getBoolean("coe619.evaluateForecast")) {
                System.out.printf(java.util.Locale.ROOT,
                    "FORECAST_HISTORY_SKIP host=%d time=%.3f originalVms=%d currentVms=%d%n",
                    host.getId(), host.getSimulation().clock(), original.size(), host.getVmList().size());
            }
            return super.isOverloaded(host);
        }
        final boolean meanModel = Boolean.getBoolean("coe619.meanForecast");
        final boolean robustModel = Boolean.getBoolean("coe619.robustForecast");
        final boolean online = Boolean.getBoolean("coe619.onlineForecast") || meanModel || robustModel;
        if (Boolean.getBoolean("coe619.evaluateForecast") || online) evaluatePending(host);
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
        final double weight = online ? onlineBlends.computeIfAbsent(host.getId(), id -> new OnlineBlend()).weight(now)
            : Boolean.getBoolean("coe619.dampedForecast") ? 0.5 : 1;
        final double trendEstimate = online || Boolean.getBoolean("coe619.dampedForecast")
            ? samples.get(0)[1] + weight * (rawForecast - samples.get(0)[1]) : rawForecast;
        final List<double[]> meanSamples = meanModel ? recentActiveSamples(host, 20) : List.of();
        final double meanEstimate = meanModel ? meanSamples.stream().mapToDouble(s -> s[1]).average().orElse(samples.get(0)[1]) : Double.NaN;
        final double selectedEstimate = meanModel ? onlineBlends.get(host.getId()).selectForecast(now, trendEstimate, meanEstimate) : trendEstimate;
        // Our scored target is allocated CPU, whose physical range is 0..1.
        // For the fixed 80% trigger this projection leaves warning decisions unchanged.
        final double[] robust = robustModel ? robustPairs.computeIfAbsent(host.getId(), id -> new RobustPairs())
            .predict(now, samples.get(0)[1]) : new double[]{Double.NaN, Double.NaN};
        final double forecast = robustModel ? robust[1] : meanModel ? Math.max(0, Math.min(1, selectedEstimate)) : selectedEstimate;
        if (Boolean.getBoolean("coe619.evaluateForecast") || online) {
            final List<Pending> queue = pending.computeIfAbsent(host.getId(), id -> new ArrayList<>());
            final double target = now + horizonSeconds;
            if (queue.stream().noneMatch(item -> Math.abs(item.target() - target) < 1e-6)) {
                queue.add(new Pending(target, forecast, samples.get(0)[1], rawForecast, trendEstimate, meanEstimate, robust[0],
                    generation.getOrDefault(host.getId(), 0L)));
                if (Boolean.getBoolean("coe619.evaluateForecast")) {
                    final String input = samples.stream().map(sample -> sample[0] + ":" + sample[1])
                        .collect(java.util.stream.Collectors.joining(";"));
                    System.out.printf(java.util.Locale.ROOT, "FORECAST_INPUT host=%d target=%.3f samples=%s%n",
                        host.getId(), target, input);
                    if (meanModel) {
                        final String meanInput = meanSamples.stream().map(s -> s[0] + ":" + s[1])
                            .collect(java.util.stream.Collectors.joining(";"));
                        System.out.printf(java.util.Locale.ROOT, "FORECAST_MEAN_INPUT host=%d target=%.3f samples=%s%n",
                            host.getId(), target, meanInput);
                    }
                    System.out.printf(java.util.Locale.ROOT, "FORECAST_BLEND host=%d target=%.3f weight=%.12f online=%s model=%s%n",
                        host.getId(), target, weight, online, robustModel ? "robust_selected" : meanModel ? "mean_selected_bounded" : "trend");
                    if (robustModel) System.out.printf(java.util.Locale.ROOT,
                        "FORECAST_ROBUST host=%d target=%.3f raw=%.12f selected=%.12f%n", host.getId(), target, robust[0], robust[1]);
                }
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
            if (item.generation() != generation.getOrDefault(host.getId(), 0L)) {
                logExcluded(host.getId(), item, "migration");
                iterator.remove();
                continue;
            }
            if (item.generation() == generation.getOrDefault(host.getId(), 0L)) {
                for (final HostStateHistoryEntry entry : host.getStateHistory()) {
                    if (entry.active() && Math.abs(entry.time() - item.target()) <= 1) {
                        final double observed = HostCpuUtilization.fractionOfCapacity(host, entry);
                        if (Boolean.getBoolean("coe619.robustForecast")) {
                            robustPairs.computeIfAbsent(host.getId(), id -> new RobustPairs())
                                .observe(item.target(), item.naive(), observed, item.robustEstimate());
                        }
                        if (Boolean.getBoolean("coe619.onlineForecast") || Boolean.getBoolean("coe619.meanForecast")) {
                            onlineBlends.computeIfAbsent(host.getId(), id -> new OnlineBlend())
                                .observe(item.target(), item.raw(), item.naive(), observed, item.trendEstimate(), item.meanEstimate());
                        }
                        if (Boolean.getBoolean("coe619.evaluateForecast")) {
                            System.out.printf(java.util.Locale.ROOT,
                                "FORECAST_ERROR host=%d target=%.3f actual=%.12f forecast=%.12f persistence=%.12f%n",
                                host.getId(), item.target(), observed, item.estimate(), item.naive());
                        }
                        iterator.remove();
                        break;
                    }
                }
            }
            if (now > item.target() + 1 && queue.contains(item)) {
                logExcluded(host.getId(), item, "missing_sample");
                iterator.remove();
            }
        }
    }

    private void logExcluded(final long hostId, final Pending item, final String reason) {
        if (Boolean.getBoolean("coe619.evaluateForecast")) {
            System.out.printf(java.util.Locale.ROOT, "FORECAST_EXCLUDED host=%d target=%.3f reason=%s%n",
                hostId, item.target(), reason);
        }
    }

    /** Close the measurement ledger without adding retrospective learning samples. */
    public void finishForecastEvaluation() {
        for (final var entry : pending.entrySet()) {
            for (final Pending item : entry.getValue()) {
                logExcluded(entry.getKey(), item, item.generation() == generation.getOrDefault(entry.getKey(), 0L)
                    ? "simulation_end" : "migration");
            }
        }
        pending.clear();
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
        return recentActiveSamples(host, windowSize);
    }

    private List<double[]> recentActiveSamples(final Host host, final int limit) {
        final List<HostStateHistoryEntry> history = host.getStateHistory();
        final List<double[]> samples = new ArrayList<>(limit);
        for (int i = history.size() - 1; i >= 0 && samples.size() < limit; i--) {
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
