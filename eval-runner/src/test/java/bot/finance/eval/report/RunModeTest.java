package bot.finance.eval.report;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.eval.Attempt;
import bot.finance.eval.observe.TargetUsage;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunModeTest {

    private static Attempt attempt(String mode) {
        Attempt attempt = new Attempt();
        attempt.measurements.usage = new TargetUsage(null, null, null, null, null, null, mode, null, null);
        return attempt;
    }

    @Test
    void returnsTheModeEveryAttemptRecorded() {
        assertThat(RunMode.of(List.of(attempt("classifier"), attempt("classifier"))))
                .isEqualTo("classifier");
    }

    @Test
    void ignoresAttemptsWithNoModeOrNoUsage() {
        assertThat(RunMode.of(List.of(attempt("classifier"), attempt(null), new Attempt())))
                .isEqualTo("classifier");
    }

    @Test
    void returnsMixedWhereTwoModesDiffer() {
        assertThat(RunMode.of(List.of(attempt("tools"), attempt("classifier")))).isEqualTo("mixed");
    }

    @Test
    void returnsNullWhereNoAttemptRecordedAMode() {
        assertThat(RunMode.of(List.of(attempt(null), new Attempt()))).isNull();
    }

    @Test
    void returnsNullForNoAttempts() {
        assertThat(RunMode.of(List.of())).isNull();
    }
}
