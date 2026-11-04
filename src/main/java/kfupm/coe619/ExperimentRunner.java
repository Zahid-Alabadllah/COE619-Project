package kfupm.coe619;

import kfupm.coe619.ConsolidationExperiment.Config;
import kfupm.coe619.ConsolidationExperiment.Policy;
import kfupm.coe619.ConsolidationExperiment.Result;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Runs THR, IQR, and the predictive LR policy on the configured scenarios and writes
 * every run to {@code results/experiments.csv} plus a mean / standard-deviation summary.
 *
 * <p>Usage: {@code ExperimentRunner [scenario ...] [--seeds N]}. Scenarios:
 * <ul>
 *   <li>{@code diurnal}: PR1 setting (4 hosts, 8 synthetic diurnal traces, finite cloudlets,
 *       idle shutdown enabled). One seed, since the trace set is fixed.</li>
 *   <li>{@code planetlab}: real PlanetLab traces of 2011-03-03; each seed draws a different
 *       random sample of 40 VMs onto 20 hosts for a full 24-hour run.</li>
 * </ul>
 */
public final class ExperimentRunner {
    private static final Path DIURNAL = Path.of("src/main/resources/planetlab/diurnal");
    private static final Path PLANETLAB = Path.of("src/main/resources/planetlab/real/20110303");
    private static final Path PLANETLAB_HEAVY = Path.of("src/main/resources/planetlab/heavy");
    private static final Path OUT_DIR = Path.of("results");
    private static final double IDLE_SHUTDOWN_SECONDS = 0;

    private ExperimentRunner() {
    }

    public static void main(final String[] args) throws IOException {
        final List<String> scenarios = new ArrayList<>();
        int seeds = 5;
        for (int i = 0; i < args.length; i++) {
            if ("--seeds".equals(args[i])) {
                seeds = Integer.parseInt(args[++i]);
            } else {
                scenarios.add(args[i]);
            }
        }
        if (scenarios.isEmpty()) {
            scenarios.addAll(List.of("diurnal", "planetlab", "planetlab_stressed"));
        }

        final List<Result> results = new ArrayList<>();
        for (final String scenario : scenarios) {
            final int runs = "diurnal".equals(scenario) ? 1 : seeds;
            for (int seed = 1; seed <= runs; seed++) {
                for (final Policy policy : Policy.values()) {
                    final Config cfg = config(scenario, policy, seed);
                    final long t0 = System.nanoTime();
                    final Result r = ConsolidationExperiment.run(cfg);
                    System.out.printf(Locale.ROOT, "%-9s %-3s seed=%d  E=%.4f kWh  SLATAH=%.6f  PDM=%.3e  "
                            + "unmet=%.3e  mig=%d  hostHours=%.2f  (%.1fs)%n",
                        scenario, policy, seed, r.energyKwh(), r.slatah(), r.pdm(), r.unmetDemand(),
                        r.migrations(), r.activeHostHours(), (System.nanoTime() - t0) / 1e9);
                    results.add(r);
                }
            }
        }

        Files.createDirectories(OUT_DIR);
        final List<String> lines = new ArrayList<>();
        lines.add(Result.csvHeader());
        results.forEach(r -> lines.add(r.toCsv()));
        final Path csv = OUT_DIR.resolve("experiments.csv");
        Files.write(csv, lines);

        final String summary = summarize(results);
        Files.writeString(OUT_DIR.resolve("summary.md"), summary);
        System.out.println();
        System.out.println(summary);
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    static Config config(final String scenario, final Policy policy, final long seed) {
        return switch (scenario) {
            case "diurnal" -> new Config(scenario, policy, ConsolidationExperiment.listTraces(DIURNAL),
                4, 20_000_000, 86_400, IDLE_SHUTDOWN_SECONDS, seed);
            case "planetlab" -> new Config(scenario, policy,
                ConsolidationExperiment.sampleTraces(PLANETLAB, 20, seed),
                10, 20_000_000, 86_400, IDLE_SHUTDOWN_SECONDS, seed);
            case "planetlab_stressed" -> new Config(scenario, policy,
                ConsolidationExperiment.sampleTraces(PLANETLAB_HEAVY, 16, seed),
                6, 20_000_000, 86_400, IDLE_SHUTDOWN_SECONDS, seed);
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };
    }

    private static String summarize(final List<Result> results) {
        final Map<String, List<Result>> groups = new LinkedHashMap<>();
        for (final Result r : results) {
            groups.computeIfAbsent(r.config().scenario() + "|" + r.config().policy(), k -> new ArrayList<>()).add(r);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("| Scenario | Policy | Runs | Energy (kWh) | SLATAH | PDM | SLAV | Unmet demand | Migrations | Active host-hours |\n");
        sb.append("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (final var e : groups.entrySet()) {
            final List<Result> g = e.getValue();
            final String[] key = e.getKey().split("\\|");
            sb.append(String.format(Locale.ROOT, "| %s | %s | %d | %s | %s | %s | %s | %s | %s | %s |%n",
                key[0], key[1], g.size(),
                stat(g, Result::energyKwh, "%.4f"), stat(g, Result::slatah, "%.6f"),
                stat(g, Result::pdm, "%.3e"), stat(g, Result::slav, "%.3e"),
                stat(g, Result::unmetDemand, "%.3e"), stat(g, r -> r.migrations(), "%.1f"),
                stat(g, Result::activeHostHours, "%.2f")));
        }
        sb.append("\nValues are mean ± sample standard deviation across runs (seeds).\n");
        return sb.toString();
    }

    private static String stat(final List<Result> g, final ToDoubleFunction<Result> f, final String fmt) {
        final SummaryStatistics s = new SummaryStatistics();
        g.forEach(r -> s.addValue(f.applyAsDouble(r)));
        final String mean = String.format(Locale.ROOT, fmt, s.getMean());
        return g.size() > 1 ? mean + " ± " + String.format(Locale.ROOT, fmt, s.getStandardDeviation()) : mean;
    }
}
