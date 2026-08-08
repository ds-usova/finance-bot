package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidSpendingPeriodException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SpendingPeriodTest {

    @Nested
    @DisplayName("constructing a spending period")
    class SpendingPeriodConstructor {

        @Test
        @DisplayName("when to equals from - then both components read back unchanged")
        void whenToEqualsFrom_thenBothComponentsReadBackUnchanged() {
            LocalDate day = LocalDate.parse("2026-07-27");

            SpendingPeriod period = new SpendingPeriod(day, day);

            assertThat(period.from()).isEqualTo(day);
            assertThat(period.to()).isEqualTo(day);
        }

        @ParameterizedTest
        @MethodSource("bot.finance.domain.value.SpendingPeriodTest#nullFromOrTo")
        @DisplayName("when from or to is null - then throws InvalidSpendingPeriodException")
        void whenFromOrToIsNull_thenThrowsInvalidSpendingPeriodException(LocalDate from, LocalDate to) {
            assertThatThrownBy(() -> new SpendingPeriod(from, to)).isInstanceOf(InvalidSpendingPeriodException.class);
        }

        @Test
        @DisplayName("when to is one day before from - then throws InvalidSpendingPeriodException")
        void whenToIsOneDayBeforeFrom_thenThrowsInvalidSpendingPeriodException() {
            LocalDate from = LocalDate.parse("2026-07-27");
            LocalDate to = from.minusDays(1);

            assertThatThrownBy(() -> new SpendingPeriod(from, to)).isInstanceOf(InvalidSpendingPeriodException.class);
        }
    }

    @Nested
    @DisplayName("parsing a spending period from written dates")
    class Of {

        @Test
        @DisplayName(
                "when two YYYY-MM-DD dates a week apart are given - then returns a period whose ends are those two dates")
        void whenTwoIso8601DatesAWeekApartAreGiven_thenReturnsPeriodWhoseEndsAreThoseTwoDates() {
            SpendingPeriod period = SpendingPeriod.of("2026-07-20", "2026-07-27");

            assertThat(period.from()).isEqualTo(LocalDate.parse("2026-07-20"));
            assertThat(period.to()).isEqualTo(LocalDate.parse("2026-07-27"));
        }

        @ParameterizedTest
        @MethodSource("bot.finance.domain.value.SpendingPeriodTest#blankValues")
        @DisplayName(
                "when from is null, empty, or whitespace only - then throws InvalidSpendingPeriodException naming the first day")
        void whenFromIsNullEmptyOrWhitespaceOnly_thenThrowsInvalidSpendingPeriodExceptionNamingFirstDay(String from) {
            assertThatThrownBy(() -> SpendingPeriod.of(from, "2026-07-27"))
                    .isInstanceOf(InvalidSpendingPeriodException.class)
                    .hasMessageContaining("first day");
        }

        @ParameterizedTest
        @MethodSource("bot.finance.domain.value.SpendingPeriodTest#blankValues")
        @DisplayName(
                "when to is null, empty, or whitespace only - then throws InvalidSpendingPeriodException naming the last day")
        void whenToIsNullEmptyOrWhitespaceOnly_thenThrowsInvalidSpendingPeriodExceptionNamingLastDay(String to) {
            assertThatThrownBy(() -> SpendingPeriod.of("2026-07-20", to))
                    .isInstanceOf(InvalidSpendingPeriodException.class)
                    .hasMessageContaining("last day");
        }

        @ParameterizedTest
        @MethodSource("bot.finance.domain.value.SpendingPeriodTest#unparseableDates")
        @DisplayName(
                "when a date is not written YYYY-MM-DD - then throws InvalidSpendingPeriodException naming the value it could not read")
        void whenDateIsNotIso8601Value_thenThrowsInvalidSpendingPeriodExceptionNamingTheValue(String badDate) {
            assertThatThrownBy(() -> SpendingPeriod.of(badDate, "2026-07-27"))
                    .isInstanceOf(InvalidSpendingPeriodException.class)
                    .hasMessageContaining(badDate);
        }

        @Test
        @DisplayName(
                "when to precedes from, both well-formed - then throws InvalidSpendingPeriodException saying the period ends before it starts")
        void whenToPrecedesFromBothWellFormed_thenThrowsInvalidSpendingPeriodExceptionSayingEndsBeforeItStarts() {
            assertThatThrownBy(() -> SpendingPeriod.of("2026-07-27", "2026-07-20"))
                    .isInstanceOf(InvalidSpendingPeriodException.class)
                    .hasMessageContaining("ends before it starts");
        }

        @Test
        @DisplayName("when a period is a decade wide and wholly in the future - then both are accepted")
        void whenPeriodIsADecadeWideAndWhollyInTheFuture_thenBothAreAccepted() {
            SpendingPeriod decadeWide = SpendingPeriod.of("2016-01-01", "2026-01-01");
            SpendingPeriod future = SpendingPeriod.of("2099-01-01", "2099-01-08");

            assertThat(decadeWide.from()).isEqualTo(LocalDate.parse("2016-01-01"));
            assertThat(decadeWide.to()).isEqualTo(LocalDate.parse("2026-01-01"));
            assertThat(future.from()).isEqualTo(LocalDate.parse("2099-01-01"));
            assertThat(future.to()).isEqualTo(LocalDate.parse("2099-01-08"));
        }
    }

    @Nested
    @DisplayName("reading a period as a half-open range of instants")
    class AsInstants {

        @Test
        @DisplayName("when the period is read as instants - then it starts at the first day's UTC midnight and "
                + "ends at the midnight after the last day")
        void whenPeriodIsReadAsInstants_thenItSpansFromFirstMidnightToTheMidnightAfterTheLastDay() {
            SpendingPeriod period = SpendingPeriod.of("2026-01-05", "2026-01-07");

            assertThat(period.startInstant()).isEqualTo(Instant.parse("2026-01-05T00:00:00Z"));
            assertThat(period.endInstantExclusive()).isEqualTo(Instant.parse("2026-01-08T00:00:00Z"));
        }

        @Test
        @DisplayName("when the period is a single day - then the range still covers that whole day")
        void whenPeriodIsASingleDay_thenTheRangeStillCoversThatWholeDay() {
            SpendingPeriod oneDay = SpendingPeriod.of("2026-01-05", "2026-01-05");

            assertThat(oneDay.startInstant()).isEqualTo(Instant.parse("2026-01-05T00:00:00Z"));
            assertThat(oneDay.endInstantExclusive()).isEqualTo(Instant.parse("2026-01-06T00:00:00Z"));
        }

        @Test
        @DisplayName("when the last day is a month end - then the range ends on the first of the next month")
        void whenLastDayIsAMonthEnd_thenTheRangeEndsOnTheFirstOfTheNextMonth() {
            SpendingPeriod february = SpendingPeriod.of("2026-02-01", "2026-02-28");

            assertThat(february.endInstantExclusive()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        }
    }

    static Stream<Arguments> nullFromOrTo() {
        LocalDate day = LocalDate.parse("2026-07-27");
        return Stream.of(Arguments.of(null, day), Arguments.of(day, null));
    }

    static Stream<String> blankValues() {
        return Stream.of(null, "", "  ");
    }

    static Stream<String> unparseableDates() {
        return Stream.of("27/07/2026", "2026-7-27", "last week", "2026-02-30");
    }
}
