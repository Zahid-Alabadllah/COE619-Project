package kfupm.coe619;

/** Standalone causal and numerical checks, outside the production source tree. */
public final class RobustPairsRegression {
    private static void close(double actual, double expected, String label) {
        if (Math.abs(actual-expected)>1e-9) throw new AssertionError(label+": "+actual);
    }
    public static void main(String[] args) {
        var learner=new PredictiveLinearRegressionPolicy.RobustPairs();
        close(learner.predict(0,.7)[1],.7,"cold start");
        for(int i=0;i<20;i++) {
            double x=i/20.0;
            learner.observe(300,x,.2+.5*x,.2+.5*x);
        }
        close(learner.predict(299,.5)[0],.5,"future observations excluded");
        close(learner.predict(300,.5)[0],.5,"same-time observations excluded");
        close(learner.predict(301,.5)[0],.45,"exact line");
        close(learner.predict(301,.5)[1],.45,"past robust accuracy selects fit");
        for(int i=0;i<20;i++)learner.observe(600,.4,.3,.9);
        close(learner.predict(601,.8)[0],.3,"constant input and old-pair eviction");
        close(learner.predict(601,.8)[1],.8,"bad fit falls back to persistence");
        close(new PredictiveLinearRegressionPolicy.RobustPairs().predict(601,.8)[1],.8,"independent host state");
        System.out.println("PASS: robust fit, causal warmup, rolling memory, selector and independent state");
    }
}
