package kfupm.coe619;

import kfupm.coe619.ConsolidationExperiment.Config;
import kfupm.coe619.ConsolidationExperiment.Policy;
import kfupm.coe619.ConsolidationExperiment.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compares policies on fixed workload manifests; no random selection at run time. */
public final class ExperimentRunner {
    private static final Path MANIFESTS = Path.of("src/main/resources/workloads");
    private static final Path OUT_DIR = Path.of("results");
    private static final List<String> CASES = List.of("diurnal", "real_a", "real_b", "real_c", "heavy");

    private ExperimentRunner() {
    }

    public static void main(final String[] args) throws IOException {
        final List<String> scenarios = new ArrayList<>();
        Path outputDir = OUT_DIR;
        for (int i = 0; i < args.length; i++) {
            if ("--output".equals(args[i])) {
                outputDir = Path.of(args[++i]);
            } else {
                scenarios.add(args[i]);
            }
        }
        if (scenarios.isEmpty()) {
            scenarios.addAll(CASES);
        }

        final List<Result> results = new ArrayList<>();
        for (final String scenario : scenarios) {

            for (final Policy policy : Policy.values()) {
                final Config cfg = config(scenario, policy);
                final long t0 = System.nanoTime();
                final Result r = ConsolidationExperiment.run(cfg);
                System.out.printf(Locale.ROOT, "%-9s %-3s E=%.4f kWh  SLATAH=%.6f  PDM=%.3e  "
                        + "unmet=%.3e  mig=%d  hostHours=%.2f  (%.1fs)%n",
                    scenario, policy, r.energyKwh(), r.slatah(), r.pdm(), r.unmetDemand(),
                    r.migrations(), r.activeHostHours(), (System.nanoTime() - t0) / 1e9);
                results.add(r);
            }
        }

        Files.createDirectories(outputDir);
        final List<String> lines = new ArrayList<>();
        lines.add(Result.csvHeader().replace("seed,", ""));
        results.forEach(r -> lines.add(r.toCsv().replaceFirst("^([^,]+,[^,]+),[^,]+,", "$1,")));
        final Path csv = outputDir.resolve("experiments.csv");
        Files.write(csv, lines);

        final String summary = summarize(results);
        Files.writeString(outputDir.resolve("summary.md"), summary);
        System.out.println();
        System.out.println(summary);
        System.out.println("Wrote " + csv.toAbsolutePath());
    }

    static Config config(final String scenario, final Policy policy) throws IOException {
        if (!CASES.contains(scenario)) {
            throw new IllegalArgumentException("Choose one of " + CASES + "; unknown case: " + scenario);
        }
        final List<Path> traces = Files.readAllLines(MANIFESTS.resolve(scenario + ".txt")).stream()
            .map(String::trim).filter(line -> !line.isEmpty()).map(Path::of).toList();
        if (traces.isEmpty() || traces.stream().anyMatch(path -> !Files.isRegularFile(path))) {
            throw new IllegalStateException("Empty manifest or missing trace for " + scenario);
        }
        final int hosts = "diurnal".equals(scenario) ? 4 : "heavy".equals(scenario) ? 6 : 10;
        // Last field retained for compatibility with historical harness records; unused here.
        return new Config(scenario, policy, traces, hosts, 20_000_000, 86_400, 0, 0);
    }

    private static String summarize(final List<Result> results) {
        final StringBuilder sb = new StringBuilder();
        sb.append("| Case | Policy | Energy (kWh) | SLATAH | PDM | SLAV | Unmet demand | Migrations | Active host-hours |\n");
        sb.append("|---|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (final Result r : results) {
            sb.append(String.format(Locale.ROOT, "| %s | %s | %.4f | %.6f | %.3e | %.3e | %.3e | %d | %.2f |%n",
                r.config().scenario(), r.config().policy(), r.energyKwh(), r.slatah(), r.pdm(), r.slav(),
                r.unmetDemand(), r.migrations(), r.activeHostHours()));
        }
        return sb.append("\nEach row is one fixed workload case, not an average.\n").toString();
    }
}
