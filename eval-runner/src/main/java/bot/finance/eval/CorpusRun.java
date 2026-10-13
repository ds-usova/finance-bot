package bot.finance.eval;

import bot.finance.eval.corpus.CaseSelection;
import bot.finance.eval.corpus.EvalCase;
import bot.finance.eval.execution.CaseExecutor;
import bot.finance.eval.report.ProgressLog;
import bot.finance.eval.report.RunOutput;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs every selected case the requested number of times, one attempt after another, and records each attempt as
 * it finishes. A repetition discarded by a UTC rollover is attempted again.
 */
final class CorpusRun {

    private static final Logger LOG = LoggerFactory.getLogger(CorpusRun.class);
    private static final int MAX_ATTEMPTS = 3;

    private final CaseSelection selection;
    private final int repetitions;
    private final CaseExecutor executor;
    private final RunOutput output;

    CorpusRun(CaseSelection selection, int repetitions, CaseExecutor executor, RunOutput output) {
        this.selection = selection;
        this.repetitions = repetitions;
        this.executor = executor;
        this.output = output;
    }

    /** Answers the run's summary. */
    Map<String, Object> run() {
        LOG.info(
                "{}: {} cases x {} repetitions, {} blocked, writing {}",
                output.runId(),
                selection.runnable().size(),
                repetitions,
                selection.blocked().size(),
                output.file());
        for (EvalCase evalCase : selection.runnable()) {
            for (int repetition = 1; repetition <= repetitions; repetition++) {
                runRepetition(evalCase, repetition);
            }
        }
        return output.finish();
    }

    private void runRepetition(EvalCase evalCase, int repetition) {
        for (int number = 1; number <= MAX_ATTEMPTS; number++) {
            Attempt attempt = executor.run(evalCase, repetition, number);
            output.record(attempt);
            ProgressLog.attempt(attempt);
            if (!attempt.attemptStatus.equals("discarded-date-rollover")) {
                return;
            }
        }
    }
}
