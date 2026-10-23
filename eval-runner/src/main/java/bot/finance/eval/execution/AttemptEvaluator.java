package bot.finance.eval.execution;

import bot.finance.eval.Attempt;
import bot.finance.eval.oracle.Constraint;
import bot.finance.eval.oracle.Evaluation;
import bot.finance.eval.oracle.Oracle;
import java.time.LocalDate;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Turns what a target turn left in an attempt into its verdict. An outcome that reports an execution failure, or
 * a declared MCP expectation with no call record, makes the attempt an error.
 */
final class AttemptEvaluator {

    private static final Set<String> FAILED_OUTCOMES = Set.of("PARTIAL", "FAILED");

    private AttemptEvaluator() {}

    static void evaluate(JsonNode declared, LocalDate runDate, Attempt attempt) {
        JsonNode expected = Constraint.resolved(declared, runDate);
        attempt.expected = expected;

        Evaluation evaluation =
                Oracle.evaluate(expected, attempt.actual.outcome, attempt.actual.proposals, attempt.actual.mcpCalls);
        attempt.checks = evaluation.checks();
        attempt.matching = new Attempt.Matching();
        attempt.matching.expected = evaluation.expectedCount();
        attempt.matching.actual = evaluation.actualCount();
        attempt.matching.matched = evaluation.matchedCount();
        attempt.matching.pairs = evaluation.matchedPairs();
        attempt.verdict = evaluation.verdict();

        if (FAILED_OUTCOMES.contains(attempt.actual.outcome)) {
            attempt.errors.add(
                    new Attempt.Failure("processing", "outcome", "the target ended " + attempt.actual.outcome, null));
            attempt.verdict = "error";
        } else if (evaluation.verdict().equals("error")) {
            attempt.errors.add(new Attempt.Failure(
                    "inspection", "evidence", "a declared MCP expectation has no call record", null));
        }

        if (!attempt.verdict.equals("error")) {
            attempt.qualityMetrics.precision = evaluation.precision();
            attempt.qualityMetrics.recall = evaluation.recall();
            attempt.qualityMetrics.f1 = evaluation.f1();
        }
    }
}
