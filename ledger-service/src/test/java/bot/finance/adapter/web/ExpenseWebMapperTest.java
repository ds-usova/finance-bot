package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.api.model.Expense;
import bot.finance.api.model.ListExpenses200Response;
import bot.finance.application.dto.DayTotal;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.dto.ExpensePage;
import bot.finance.domain.exception.InvalidExpenseFilterException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseFilter;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openapitools.jackson.nullable.JsonNullable;

class ExpenseWebMapperTest {

    private static final Instant FIRST_CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    private static final Instant SECOND_CREATED_AT = Instant.parse("2026-01-02T08:30:00Z");

    @Nested
    @DisplayName("building a filter from the list-expenses query parameters")
    class ToFilter {

        @Test
        @DisplayName("when every query parameter is absent - then the filter carries the defaults and no narrowing")
        void whenEveryQueryParameterIsAbsent_thenFilterCarriesDefaultsAndNoOptionalFields() {
            ExpenseFilter filter = ExpenseWebMapper.toFilter(null, null, null, null, null, null);

            assertThat(filter.limit()).isEqualTo(ExpenseFilter.DEFAULT_LIMIT);
            assertThat(filter.offset()).isZero();
            assertThat(filter.status()).isNull();
            assertThat(filter.categoryId()).isNull();
            assertThat(filter.period()).isNull();
        }

        @Test
        @DisplayName("when every query parameter is given - then each lands on the filter, the two days as a period")
        void whenEveryFieldIsGiven_thenEachLandsOnTheFilterAndThePeriodCarriesTheTwoDays() {
            LocalDate from = LocalDate.of(2026, 1, 1);
            LocalDate to = LocalDate.of(2026, 1, 31);

            ExpenseFilter filter = ExpenseWebMapper.toFilter(20, 10, "RECORDED", 5L, from, to);

            assertThat(filter.limit()).isEqualTo(20);
            assertThat(filter.offset()).isEqualTo(10);
            assertThat(filter.status()).isEqualTo(ExpenseStatus.RECORDED);
            assertThat(filter.categoryId()).isEqualTo(5L);
            assertThat(filter.period()).isEqualTo(new SpendingPeriod(from, to));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("oneSidedPeriods")
        @DisplayName("when only one of from and to is given - then throws InvalidSpendingPeriodException")
        void whenPeriodHasOnlyOneDay_thenThrowsInvalidSpendingPeriodException(
                String description, LocalDate from, LocalDate to) {
            assertThatThrownBy(() -> ExpenseWebMapper.toFilter(null, null, null, null, from, to))
                    .isInstanceOf(InvalidSpendingPeriodException.class);
        }

        static Stream<Arguments> oneSidedPeriods() {
            return Stream.of(
                    arguments("from without to", LocalDate.of(2026, 1, 1), null),
                    arguments("to without from", null, LocalDate.of(2026, 1, 31)));
        }

        @Test
        @DisplayName("when a to falls before the from - then throws InvalidSpendingPeriodException")
        void whenToFallsBeforeFrom_thenThrowsInvalidSpendingPeriodException() {
            LocalDate from = LocalDate.of(2026, 2, 1);
            LocalDate to = LocalDate.of(2026, 1, 1);

            assertThatThrownBy(() -> ExpenseWebMapper.toFilter(null, null, null, null, from, to))
                    .isInstanceOf(InvalidSpendingPeriodException.class);
        }

        @Test
        @DisplayName("when a limit above the maximum is given - then throws InvalidExpenseFilterException")
        void whenLimitIsAboveMaximum_thenThrowsInvalidExpenseFilterException() {
            assertThatThrownBy(() -> ExpenseWebMapper.toFilter(ExpenseFilter.MAX_LIMIT + 1, 0, null, null, null, null))
                    .isInstanceOf(InvalidExpenseFilterException.class);
        }

        @Test
        @DisplayName("when a status no constant matches is given - then throws InvalidExpenseFilterException naming "
                + "the parameter")
        void whenStatusMatchesNoConstant_thenThrowsInvalidExpenseFilterException() {
            assertThatThrownBy(() -> ExpenseWebMapper.toFilter(null, null, "FOO", null, null, null))
                    .isInstanceOf(InvalidExpenseFilterException.class)
                    .hasMessageContaining("status");
        }
    }

    @Nested
    @DisplayName("mapping a page of stored expenses onto the list-expenses response")
    class ToResponse {

        @Test
        @DisplayName("when a page of two entries, one PENDING and one RECORDED, is given - then every field of "
                + "each entry is mapped")
        void whenPageHasTwoEntriesWithDifferentStatuses_thenEveryFieldIsMapped() {
            ListExpenses200Response response = ExpenseWebMapper.toResponse(pageOfTwoEntries());

            assertThat(response.getItems()).hasSize(2);
            Expense firstItem = response.getItems().get(0);
            assertThat(firstItem.getId()).isEqualTo(1L);
            assertThat(firstItem.getStatus().name()).isEqualTo(ExpenseStatus.PENDING.name());
            assertThat(firstItem.getCategoryId()).isEqualTo(10L);
            assertThat(firstItem.getDescription()).isEqualTo("Milk");
            assertThat(firstItem.getMerchant()).isEqualTo(JsonNullable.of("Corner Shop"));
            assertThat(firstItem.getMoney().getAmount()).isEqualTo("15.00");
            assertThat(firstItem.getMoney().getCurrency()).isEqualTo("€");
            assertThat(firstItem.getMoney().getSeparator()).isEqualTo("");
            assertThat(firstItem.getCreatedAt().toInstant()).isEqualTo(FIRST_CREATED_AT);
            Expense secondItem = response.getItems().get(1);
            assertThat(secondItem.getId()).isEqualTo(2L);
            assertThat(secondItem.getStatus().name()).isEqualTo(ExpenseStatus.RECORDED.name());
            assertThat(secondItem.getCategoryId()).isEqualTo(20L);
            assertThat(secondItem.getDescription()).isEqualTo("Bus ticket");
            assertThat(secondItem.getMerchant()).isEqualTo(JsonNullable.of("City Transit"));
            assertThat(secondItem.getMoney().getAmount()).isEqualTo("3.50");
            assertThat(secondItem.getMoney().getCurrency()).isEqualTo("€");
            assertThat(secondItem.getMoney().getSeparator()).isEqualTo("");
            assertThat(secondItem.getCreatedAt().toInstant()).isEqualTo(SECOND_CREATED_AT);
        }

        @Test
        @DisplayName("when a page carries a total larger than itself - then the response carries the page's own "
                + "limit, offset and total")
        void whenPageTotalIsLargerThanThePage_thenResponseCarriesThePagesOwnMetadata() {
            ListExpenses200Response response = ExpenseWebMapper.toResponse(pageOfTwoEntries());

            assertThat(response.getLimit()).isEqualTo(20);
            assertThat(response.getOffset()).isZero();
            assertThat(response.getTotal()).isEqualTo(57L);
        }

        /** A page of two entries, one PENDING and one RECORDED, whose total is larger than the page itself. */
        private static ExpensePage pageOfTwoEntries() {
            ExpenseEntry first = new ExpenseEntry(
                    ExpenseStatus.PENDING,
                    1L,
                    10L,
                    "Milk",
                    Optional.of("Corner Shop"),
                    new Money(1500L, CurrencyCode.of("EUR")),
                    FIRST_CREATED_AT);
            ExpenseEntry second = new ExpenseEntry(
                    ExpenseStatus.RECORDED,
                    2L,
                    20L,
                    "Bus ticket",
                    Optional.of("City Transit"),
                    new Money(350L, CurrencyCode.of("EUR")),
                    SECOND_CREATED_AT);
            return ExpensePage.of(List.of(first, second), 20, 0, 57L);
        }

        @Test
        @DisplayName("when an entry has no merchant - then the response omits the merchant rather than carrying an "
                + "empty string")
        void whenEntryHasNoMerchant_thenResponseOmitsMerchant() {
            ExpenseEntry entry = new ExpenseEntry(
                    ExpenseStatus.PENDING,
                    3L,
                    30L,
                    "Coffee",
                    Optional.empty(),
                    new Money(400L, CurrencyCode.of("EUR")),
                    Instant.parse("2026-01-03T09:00:00Z"));
            ExpensePage page = ExpensePage.of(List.of(entry), 50, 0, 1L);

            ListExpenses200Response response = ExpenseWebMapper.toResponse(page);

            assertThat(response.getItems().get(0).getMerchant()).isEqualTo(JsonNullable.undefined());
        }

        @Test
        @DisplayName("when a page's one entry is in EUR - then the item's money carries the amount, the euro "
                + "symbol and an empty separator")
        void whenEntryIsInEur_thenItemMoneyCarriesAmountCurrencyAndEmptySeparator() {
            ExpenseEntry entry = new ExpenseEntry(
                    ExpenseStatus.RECORDED,
                    4L,
                    40L,
                    "Lunch",
                    Optional.empty(),
                    new Money(1250L, CurrencyCode.of("EUR")),
                    Instant.parse("2026-01-04T09:00:00Z"));
            ExpensePage page = ExpensePage.of(List.of(entry), 50, 0, 1L);

            ListExpenses200Response response = ExpenseWebMapper.toResponse(page);

            Expense item = response.getItems().get(0);
            assertThat(item.getMoney().getAmount()).isEqualTo("12.50");
            assertThat(item.getMoney().getCurrency()).isEqualTo("€");
            assertThat(item.getMoney().getSeparator()).isEqualTo("");
        }

        @Test
        @DisplayName("when a page's one entry is in CHF - then the item's money carries the ISO code as currency "
                + "and a separator of one space")
        void whenEntryIsInChf_thenItemMoneyCarriesIsoCodeCurrencyAndOneSpaceSeparator() {
            ExpenseEntry entry = new ExpenseEntry(
                    ExpenseStatus.RECORDED,
                    5L,
                    40L,
                    "Watch",
                    Optional.empty(),
                    new Money(124500L, CurrencyCode.of("CHF")),
                    Instant.parse("2026-01-05T09:00:00Z"));
            ExpensePage page = ExpensePage.of(List.of(entry), 50, 0, 1L);

            ListExpenses200Response response = ExpenseWebMapper.toResponse(page);

            Expense item = response.getItems().get(0);
            assertThat(item.getMoney().getAmount()).isEqualTo("1,245.00");
            assertThat(item.getMoney().getCurrency()).isEqualTo("CHF");
            assertThat(item.getMoney().getSeparator()).isEqualTo(" ");
        }

        @Test
        @DisplayName("when a day's total holds a EUR and a JPY figure - then both render, EUR first, in the "
                + "page's order")
        void whenDayTotalHoldsEurAndJpyFigure_thenBothRenderInThePagesOrder() {
            LocalDate day = LocalDate.of(2026, 1, 5);
            DayTotal dayTotal = new DayTotal(
                    day, List.of(new Money(1250L, CurrencyCode.of("EUR")), new Money(900L, CurrencyCode.of("JPY"))));
            ExpensePage page = new ExpensePage(List.of(), 20, 0, 0L, List.of(dayTotal));

            ListExpenses200Response response = ExpenseWebMapper.toResponse(page);

            assertThat(response.getDayTotals()).hasSize(1);
            assertThat(response.getDayTotals().get(0).getDay()).isEqualTo(day);
            assertThat(response.getDayTotals().get(0).getAmounts()).hasSize(2);
            assertThat(response.getDayTotals().get(0).getAmounts().get(0).getAmount())
                    .isEqualTo("12.50");
            assertThat(response.getDayTotals().get(0).getAmounts().get(0).getCurrency())
                    .isEqualTo("€");
            assertThat(response.getDayTotals().get(0).getAmounts().get(1).getAmount())
                    .isEqualTo("900");
            assertThat(response.getDayTotals().get(0).getAmounts().get(1).getCurrency())
                    .isEqualTo("¥");
        }

        @Test
        @DisplayName("when a page carries entries but no dayTotals - then the response's dayTotals is an empty "
                + "list rather than absent")
        void whenPageCarriesEntriesButNoDayTotals_thenResponseDayTotalsIsEmptyListRatherThanAbsent() {
            ExpenseEntry entry = new ExpenseEntry(
                    ExpenseStatus.RECORDED,
                    6L,
                    40L,
                    "Dinner",
                    Optional.empty(),
                    new Money(2000L, CurrencyCode.of("EUR")),
                    Instant.parse("2026-01-06T09:00:00Z"));
            ExpensePage page = new ExpensePage(List.of(entry), 50, 0, 1L, List.of());

            ListExpenses200Response response = ExpenseWebMapper.toResponse(page);

            assertThat(response.getDayTotals()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("when a page has no entries and no dayTotals - then the response carries an empty items list "
                + "and an empty dayTotals list")
        void whenPageHasNoEntriesAndNoDayTotals_thenResponseCarriesEmptyItemsAndEmptyDayTotals() {
            ExpensePage page = ExpensePage.of(List.of(), 50, 0, 0L);

            ListExpenses200Response response = ExpenseWebMapper.toResponse(page);

            assertThat(response.getItems()).isNotNull().isEmpty();
            assertThat(response.getDayTotals()).isNotNull().isEmpty();
        }
    }
}
