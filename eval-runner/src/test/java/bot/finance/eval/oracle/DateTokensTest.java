package bot.finance.eval.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateTokensTest {

    // A Tuesday.
    private static final LocalDate RUN_DATE = LocalDate.of(2026, 10, 13);

    @Test
    void resolvesEveryTokenFromTheRunDate() {
        assertThat(DateTokens.resolve("run-date", RUN_DATE)).isEqualTo("2026-10-13");
        assertThat(DateTokens.resolve("run-date-minus-1-day", RUN_DATE)).isEqualTo("2026-10-12");
        assertThat(DateTokens.resolve("previous-week-monday", RUN_DATE)).isEqualTo("2026-10-05");
        assertThat(DateTokens.resolve("previous-week-sunday", RUN_DATE)).isEqualTo("2026-10-11");
        assertThat(DateTokens.resolve("current-month-first-day", RUN_DATE)).isEqualTo("2026-10-01");
        assertThat(DateTokens.resolve("most-recent-friday", RUN_DATE)).isEqualTo("2026-10-09");
        assertThat(DateTokens.resolve("current-year-october-10", RUN_DATE)).isEqualTo("2026-10-10");
        assertThat(DateTokens.resolve("current-year-october-12", RUN_DATE)).isEqualTo("2026-10-12");
    }

    @Test
    void previousWeekOfAMondayIsTheWeekBeforeIt() {
        LocalDate monday = LocalDate.of(2026, 10, 12);

        assertThat(DateTokens.resolve("previous-week-monday", monday)).isEqualTo("2026-10-05");
        assertThat(DateTokens.resolve("previous-week-sunday", monday)).isEqualTo("2026-10-11");
    }

    @Test
    void mostRecentFridayOnAFridayIsThatDay() {
        assertThat(DateTokens.resolve("most-recent-friday", LocalDate.of(2026, 10, 9)))
                .isEqualTo("2026-10-09");
    }

    @Test
    void leavesALiteralAlone() {
        assertThat(DateTokens.resolve("1969-12-31", RUN_DATE)).isEqualTo("1969-12-31");
        assertThat(DateTokens.isToken("1969-12-31")).isFalse();
        assertThat(DateTokens.isToken("run-date")).isTrue();
    }
}
