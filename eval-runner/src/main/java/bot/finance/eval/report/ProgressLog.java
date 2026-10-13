package bot.finance.eval.report;

import bot.finance.eval.Attempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One line per finished attempt, so a long run shows where it is and what went wrong. */
public final class ProgressLog {

    private static final Logger LOG = LoggerFactory.getLogger(ProgressLog.class);

    private ProgressLog() {}

    public static void attempt(Attempt attempt) {
        String verdict = attempt.attemptStatus.equals("completed") ? attempt.verdict : "rollover";
        if (attempt.errors.isEmpty()) {
            LOG.info(
                    "{} rep {} att {}: {} {} in {} ms",
                    attempt.caseId,
                    attempt.repetition,
                    attempt.attempt,
                    verdict,
                    attempt.actual.outcome,
                    attempt.measurements.targetTurnMs);
        } else {
            LOG.warn(
                    "{} rep {} att {}: {} at {}: {}",
                    attempt.caseId,
                    attempt.repetition,
                    attempt.attempt,
                    verdict,
                    attempt.errors.getFirst().stage(),
                    attempt.errors.getFirst().message());
        }
    }
}
