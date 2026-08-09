package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExpensePageTest {

    @Nested
    @DisplayName("building a page from a list of expense entries")
    class Of {

        @Test
        @DisplayName("when a JPY entry and a EUR entry RECORDED on the same UTC day are given - "
                + "then that day holds both, EUR before JPY")
        void whenAJpyAndAEurRecordedEntryShareAUtcDay_thenThatDayHoldsBothEurBeforeJpy() {
            Instant createdAt = Instant.parse("2026-08-01T10:00:00Z");
            ExpenseEntry jpyEntry = recordedEntry(900L, "JPY", createdAt);
            ExpenseEntry eurEntry = recordedEntry(1250L, "EUR", createdAt.plusSeconds(60));

            ExpensePage page = ExpensePage.of(List.of(jpyEntry, eurEntry), 20, 0, 2L);

            assertThat(page.dayTotals()).hasSize(1);
            DayTotal dayTotal = page.dayTotals().get(0);
            assertThat(dayTotal.day()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(dayTotal.amounts())
                    .containsExactly(new Money(1250L, CurrencyCode.of("EUR")), new Money(900L, CurrencyCode.of("JPY")));
        }

        @Test
        @DisplayName("when a day holds a RECORDED entry and a PENDING entry of the same amount - "
                + "then only the RECORDED one counts")
        void whenADayHoldsARecordedAndAPendingEntryOfTheSameAmount_thenOnlyTheRecordedOneCounts() {
            Instant createdAt = Instant.parse("2026-08-01T09:00:00Z");
            ExpenseEntry recorded = entry(ExpenseStatus.RECORDED, 500L, "EUR", createdAt);
            ExpenseEntry pending = entry(ExpenseStatus.PENDING, 500L, "EUR", createdAt.plusSeconds(60));

            ExpensePage page = ExpensePage.of(List.of(recorded, pending), 20, 0, 2L);

            assertThat(page.dayTotals()).hasSize(1);
            assertThat(page.dayTotals().get(0).amounts()).containsExactly(new Money(500L, CurrencyCode.of("EUR")));
        }

        @Test
        @DisplayName("when three RECORDED EUR entries share a UTC day - "
                + "then that day holds one Money, their sum, in EUR")
        void whenThreeRecordedEurEntriesShareAUtcDay_thenThatDayHoldsTheirSumInEur() {
            Instant createdAt = Instant.parse("2026-08-01T08:00:00Z");
            ExpenseEntry first = recordedEntry(100L, "EUR", createdAt);
            ExpenseEntry second = recordedEntry(200L, "EUR", createdAt.plusSeconds(60));
            ExpenseEntry third = recordedEntry(300L, "EUR", createdAt.plusSeconds(120));

            ExpensePage page = ExpensePage.of(List.of(first, second, third), 20, 0, 3L);

            assertThat(page.dayTotals()).hasSize(1);
            assertThat(page.dayTotals().get(0).amounts()).containsExactly(new Money(600L, CurrencyCode.of("EUR")));
        }

        @Test
        @DisplayName("when entries span three UTC days - then dayTotals holds three elements, newest day first")
        void whenEntriesSpanThreeUtcDays_thenDayTotalsHoldsThreeElementsNewestDayFirst() {
            ExpenseEntry day2 = recordedEntry(200L, "EUR", Instant.parse("2026-08-02T08:00:00Z"));
            ExpenseEntry day3 = recordedEntry(300L, "EUR", Instant.parse("2026-08-03T08:00:00Z"));
            ExpenseEntry day1 = recordedEntry(100L, "EUR", Instant.parse("2026-08-01T08:00:00Z"));

            ExpensePage page = ExpensePage.of(List.of(day2, day3, day1), 20, 0, 3L);

            assertThat(page.dayTotals())
                    .extracting(DayTotal::day)
                    .containsExactly(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1));
        }

        @Test
        @DisplayName("when entries straddle midnight UTC on the same local evening - "
                + "then they land in two DayTotals by UTC day")
        void whenEntriesStraddleMidnightUtc_thenTheyLandInTwoDayTotalsByUtcDay() {
            ExpenseEntry beforeMidnight = recordedEntry(100L, "EUR", Instant.parse("2026-08-01T23:30:00Z"));
            ExpenseEntry afterMidnight = recordedEntry(200L, "EUR", Instant.parse("2026-08-02T00:30:00Z"));

            ExpensePage page = ExpensePage.of(List.of(beforeMidnight, afterMidnight), 20, 0, 2L);

            assertThat(page.dayTotals())
                    .extracting(DayTotal::day)
                    .containsExactly(LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 1));
        }

        @Test
        @DisplayName("when one UTC day is all PENDING and another holds a RECORDED entry - "
                + "then dayTotals holds only the RECORDED day")
        void whenOneDayIsAllPendingAndAnotherHasARecordedEntry_thenDayTotalsHoldsOnlyTheRecordedDay() {
            ExpenseEntry pendingDay = entry(ExpenseStatus.PENDING, 100L, "EUR", Instant.parse("2026-08-01T08:00:00Z"));
            ExpenseEntry recordedDay = recordedEntry(200L, "EUR", Instant.parse("2026-08-02T08:00:00Z"));

            ExpensePage page = ExpensePage.of(List.of(pendingDay, recordedDay), 20, 0, 2L);

            assertThat(page.dayTotals()).extracting(DayTotal::day).containsExactly(LocalDate.of(2026, 8, 2));
        }

        @Test
        @DisplayName("when every entry in the page is PENDING - "
                + "then dayTotals is empty and items still carries every entry")
        void whenEveryEntryIsPending_thenDayTotalsIsEmptyAndItemsCarriesEveryEntry() {
            ExpenseEntry first = entry(ExpenseStatus.PENDING, 100L, "EUR", Instant.parse("2026-08-01T08:00:00Z"));
            ExpenseEntry second = entry(ExpenseStatus.PENDING, 200L, "EUR", Instant.parse("2026-08-02T08:00:00Z"));

            ExpensePage page = ExpensePage.of(List.of(first, second), 20, 0, 2L);

            assertThat(page.dayTotals()).isEmpty();
            assertThat(page.items()).containsExactly(first, second);
        }

        @Test
        @DisplayName("when there are no entries at all - "
                + "then dayTotals and items are empty and the metadata matches the arguments")
        void whenThereAreNoEntries_thenDayTotalsAndItemsAreEmptyAndMetadataMatchesTheArguments() {
            ExpensePage page = ExpensePage.of(List.of(), 20, 10, 57L);

            assertThat(page.dayTotals()).isEmpty();
            assertThat(page.items()).isEmpty();
            assertThat(page.limit()).isEqualTo(20);
            assertThat(page.offset()).isEqualTo(10);
            assertThat(page.total()).isEqualTo(57L);
        }

        @Test
        @DisplayName("when a page carries three of a day's five RECORDED entries - "
                + "then that day's figure sums those three alone")
        void whenPageCarriesThreeOfADaysFiveRecordedEntries_thenThatDaysFigureSumsThoseThreeAlone() {
            Instant createdAt = Instant.parse("2026-08-01T08:00:00Z");
            ExpenseEntry first = recordedEntry(100L, "EUR", createdAt);
            ExpenseEntry second = recordedEntry(200L, "EUR", createdAt.plusSeconds(60));
            ExpenseEntry third = recordedEntry(300L, "EUR", createdAt.plusSeconds(120));

            ExpensePage page = ExpensePage.of(List.of(first, second, third), 3, 0, 5L);

            assertThat(page.dayTotals()).hasSize(1);
            assertThat(page.dayTotals().get(0).amounts()).containsExactly(new Money(600L, CurrencyCode.of("EUR")));
        }

        @Test
        @DisplayName("when a page of entries is built - "
                + "then items, limit, offset and total are the arguments given, unchanged")
        void whenAPageOfEntriesIsBuilt_thenItemsLimitOffsetAndTotalAreTheArgumentsGivenUnchanged() {
            ExpenseEntry recorded = recordedEntry(100L, "EUR", Instant.parse("2026-08-01T08:00:00Z"));
            ExpenseEntry pending = entry(ExpenseStatus.PENDING, 200L, "EUR", Instant.parse("2026-08-02T08:00:00Z"));
            List<ExpenseEntry> items = List.of(recorded, pending);

            ExpensePage page = ExpensePage.of(items, 15, 5, 42L);

            assertThat(page.items()).isEqualTo(items);
            assertThat(page.limit()).isEqualTo(15);
            assertThat(page.offset()).isEqualTo(5);
            assertThat(page.total()).isEqualTo(42L);
        }

        private static ExpenseEntry recordedEntry(long minorUnits, String currencyCode, Instant createdAt) {
            return entry(ExpenseStatus.RECORDED, minorUnits, currencyCode, createdAt);
        }

        private static ExpenseEntry entry(
                ExpenseStatus status, long minorUnits, String currencyCode, Instant createdAt) {
            return new ExpenseEntry(
                    status,
                    1L,
                    1L,
                    "expense",
                    Optional.empty(),
                    new Money(minorUnits, CurrencyCode.of(currencyCode)),
                    createdAt);
        }
    }
}
