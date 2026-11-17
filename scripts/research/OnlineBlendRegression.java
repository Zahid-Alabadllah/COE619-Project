package kfupm.coe619;

/** Standalone meaningful checks for causal learning; not another production class. */
public final class OnlineBlendRegression {
    private static void close(double actual, double expected, String label) {
        if (Math.abs(actual - expected) > 1e-9) throw new AssertionError(label + ": " + actual);
    }

    public static void main(String[] args) {
        var learner = new PredictiveLinearRegressionPolicy.OnlineBlend();
        close(learner.weight(0), .5, "cold start");
        for (int i = 0; i < 10; i++) learner.observe(300, .8, .4, .4);
        close(learner.weight(299), .5, "future outcomes excluded");
        close(learner.weight(300), .5, "same-time outcomes excluded");
        close(learner.weight(301), 0, "misleading trend gets zero weight");
        for (int i = 0; i < 20; i++) learner.observe(600, .8, .4, 1.2);
        close(learner.weight(601), 1, "old errors evicted and upper bound enforced");
        close(new PredictiveLinearRegressionPolicy.OnlineBlend().weight(601), .5, "independent host state");
        var negative = new PredictiveLinearRegressionPolicy.OnlineBlend();
        var flat = new PredictiveLinearRegressionPolicy.OnlineBlend();
        for (int i = 0; i < 10; i++) {
            negative.observe(300, .8, .4, .2);
            flat.observe(300, .4, .4, .8);
        }
        close(negative.weight(301), 0, "lower bound enforced");
        close(flat.weight(301), .5, "zero denominator fallback");
        System.out.println("PASS: causality, cold start, bounded weight, rolling memory and independent state");
    }
}
