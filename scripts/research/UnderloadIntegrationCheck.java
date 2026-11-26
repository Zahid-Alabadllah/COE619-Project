package kfupm.coe619;
import java.nio.file.*;
import java.util.*;
/** Separate synthetic correctness case; it does not add a production workload option. */
public final class UnderloadIntegrationCheck {
    public static void main(String[] args) throws Exception {
        Path inputs=Path.of(args[0]),output=Path.of(args[1]);
        List<Path> traces=Files.readAllLines(inputs.resolve("manifest.txt")).stream().filter(s->!s.isBlank()).map(Path::of).toList();
        List<String> rows=new ArrayList<>();rows.add(ConsolidationExperiment.Result.csvHeader());
        for(var policy:ConsolidationExperiment.Policy.values()) {
            var config=new ConsolidationExperiment.Config("controlled_underload",policy,traces,3,1000000000,86400,0,0);
            var result=ConsolidationExperiment.run(config);
            if(result.vmsCreated()!=12 || result.cloudletsFinished()!=9 || result.endTime()!=86400)
                throw new AssertionError("Lost work or mismatched window: "+result.toCsv());
            rows.add(result.toCsv());
        }
        Files.createDirectories(output);Files.write(output.resolve("experiments.csv"),rows);
        System.out.println("CONTROLLED_CHECK_COMPLETE: same12VMs,9short jobs completed,3long jobs retained, exact86400seconds.");
    }
}
