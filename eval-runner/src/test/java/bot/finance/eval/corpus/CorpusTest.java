package bot.finance.eval.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Reads the repository's own corpus, so a case the runner cannot execute fails the build rather than a run. */
class CorpusTest {

    private static final Set<String> KNOWN_ACTIONS = Set.of(
            "verify-fixture",
            "file-learning-message",
            "wait-for-terminal-outcome",
            "verify-learning-proposals",
            "set-learning-classification",
            "resolve-learning-proposals",
            "wait-for-memory-readiness",
            "verify-call-budget-enforcement",
            "file-target-message",
            "inspect-application-state",
            "inspect-mcp-calls",
            "collect-recall-evidence",
            "collect-evidence",
            "cleanup-case-data",
            "verify-fixture-restored");

    private final List<EvalCase> cases = Corpus.load(Path.of("../docs/eval/corpus"));

    @Test
    void loadsEveryCaseInNumericOrder() {
        List<String> ids = cases.stream().map(EvalCase::id).toList();

        assertThat(ids).hasSize(59);
        assertThat(ids.getFirst()).isEqualTo("1-001-expense-statement");
        assertThat(ids.getLast()).isEqualTo("10-006-current-input-overrides-history");
    }

    @Test
    void everyStepIsAnActionTheRunnerKnowsAndNamesAnExistingLearningEntry() {
        for (EvalCase evalCase : cases) {
            for (Step step : evalCase.steps()) {
                assertThat(KNOWN_ACTIONS).as(evalCase.id()).contains(step.action());
                if (step.learningId() != null) {
                    assertThat(evalCase.learningEntry(step.learningId()))
                            .as(evalCase.id())
                            .isPresent();
                }
                if (step.message() != null && !step.message().equals("target")) {
                    assertThat(evalCase.learningEntry(step.message()))
                            .as(evalCase.id())
                            .isPresent();
                }
            }
        }
    }

    @Test
    void comparesIdsByDimensionThenLocalNumber() {
        assertThat(Corpus.compareIds("2-001-x", "10-001-y")).isNegative();
        assertThat(Corpus.compareIds("8-010-x", "8-002-y")).isPositive();
    }
}
