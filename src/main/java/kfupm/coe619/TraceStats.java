package kfupm.coe619;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Prints simple statistics for each PlanetLab trace folder, so every dataset number quoted in
 * the reports can be reproduced with one command.
 *
 * <p>Usage: {@code TraceStats [folder ...]} (defaults to the folders used by the experiments).
 * Each trace file holds one CPU-utilization value (percent) per 5-minute interval.</p>
 */
public final class TraceStats {
    private static final List<String> DEFAULT = List.of(
        "src/main/resources/planetlab/diurnal",
        "src/main/resources/planetlab/real/20110303",
        "src/main/resources/planetlab/heavy");

    private TraceStats() {
    }

    public static void main(final String[] args) throws IOException {
        final List<String> dirs = args.length > 0 ? List.of(args) : DEFAULT;
        System.out.println("| Folder | Traces | Mean of trace means (%) | Min trace mean (%) | Max trace mean (%) | Max single sample (%) |");
        System.out.println("|---|---:|---:|---:|---:|---:|");
        for (final String d : dirs) {
            row(d, ConsolidationExperiment.listTraces(Path.of(d)));
        }
    }

    private static void row(final String d, final List<Path> files) throws IOException {
        {
            double sumMeans = 0;
            double minMean = Double.MAX_VALUE;
            double maxMean = 0;
            double maxSample = 0;
            for (final Path f : files) {
                final double[] v = read(f);
                double s = 0;
                for (final double x : v) {
                    s += x;
                    maxSample = Math.max(maxSample, x);
                }
                final double mean = s / v.length;
                sumMeans += mean;
                minMean = Math.min(minMean, mean);
                maxMean = Math.max(maxMean, mean);
            }
            System.out.printf(Locale.ROOT, "| %s | %d | %.2f | %.2f | %.2f | %.0f |%n",
                d, files.size(), sumMeans / files.size(), minMean, maxMean, maxSample);
        }
    }

    static double[] read(final Path f) throws IOException {
        return Files.readAllLines(f).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .mapToDouble(Double::parseDouble)
            .toArray();
    }

    static double mean(final Path f) throws IOException {
        final double[] v = read(f);
        double s = 0;
        for (final double x : v) {
            s += x;
        }
        return s / v.length;
    }
}
