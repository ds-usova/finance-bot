package bot.finance.eval;

import bot.finance.eval.report.CompareCommand;
import bot.finance.eval.report.RunOutput;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the corpus and writes {@code <out>/<label>.json}; or, given {@code compare <before.json> <after.json>},
 * compares two runs.
 */
public final class EvalRunner {

    private static final Logger LOG = LoggerFactory.getLogger(EvalRunner.class);

    private EvalRunner() {}

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("compare")) {
            if (args.length != 3) {
                throw new IllegalArgumentException("usage: compare <before.json> <after.json>");
            }
            CompareCommand.run(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        RunnerConfig config = RunnerConfig.from(args, System.getenv());
        LOG.info("summary: {}", RunOutput.json(Wiring.corpusRun(config).run()));
    }
}
