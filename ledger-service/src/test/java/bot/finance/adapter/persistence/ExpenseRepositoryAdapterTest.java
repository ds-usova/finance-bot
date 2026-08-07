package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.CurrencyTotal;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseFilter;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.SpendingPeriod;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@PersistenceAdapterTest
@Import(ExpenseRepositoryAdapter.class)
class ExpenseRepositoryAdapterTest {

    @Autowired
    private ExpenseRepositoryAdapter adapter;

    @Autowired
    private ExpenseEntityRepository expenseEntityRepository;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("creating an expense")
    class Create {

        @Test
        @DisplayName(
                "when called with an expense carrying a merchant - then the row holds what was given and carries a generated id")
        void whenCalledWithMerchant_thenRowWrittenWithGivenFieldsAndReturnedExpenseCarriesGeneratedId() {
            long userId = storedUserId("merchant-expense-user");
            long categoryId = storedGroupingId(userId, "Groceries");
            Expense expense = Expense.newExpense(
                    userId,
                    categoryId,
                    "Weekly shop",
                    Optional.of("Trader Joe's"),
                    new Money(1500, CurrencyCode.of("USD")),
                    Instant.now());

            Expense created = adapter.create(expense);

            assertThat(created.id()).isPresent();
            List<ExpenseEntity> rows = expenseRowsFor(userId);
            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.categoryId()).isEqualTo(categoryId);
                assertThat(row.description()).isEqualTo("Weekly shop");
                assertThat(row.merchant()).isEqualTo("Trader Joe's");
                assertThat(row.amountMinorUnits()).isEqualTo(1500);
                assertThat(row.currencyCode()).isEqualTo("USD");
                assertThat(row.messageReference()).isNull();
            });
        }

        @Test
        @DisplayName(
                "when called with a stored user, a stored category, and an unstored expense with no merchant - then the row's merchant column is null and the returned expense's merchant is empty")
        void whenCalledWithNoMerchant_thenRowMerchantColumnIsNullAndReturnedExpenseMerchantIsEmpty() {
            long userId = storedUserId("no-merchant-expense-user");
            long categoryId = storedGroupingId(userId, "Utilities");
            Expense expense = Expense.newExpense(
                    userId,
                    categoryId,
                    "Electric bill",
                    Optional.empty(),
                    new Money(4200, CurrencyCode.of("USD")),
                    Instant.now());

            Expense created = adapter.create(expense);

            assertThat(created.merchant()).isEmpty();
            List<ExpenseEntity> rows = expenseRowsFor(userId);
            assertThat(rows).singleElement().satisfies(row -> assertThat(row.merchant())
                    .isNull());
        }

        @Test
        @DisplayName(
                "when called with an expense stamped with an instant carrying nanosecond precision and the row is read back - then both timestamps equal that instant truncated to microseconds")
        void whenInstantCarriesNanosecondPrecision_thenReadBackTimestampsAreTruncatedToMicroseconds() {
            long userId = storedUserId("nanosecond-expense-user");
            long categoryId = storedGroupingId(userId, "Dining");
            Instant nanosecondInstant = Instant.parse("2026-01-15T10:30:00.123456789Z");
            Expense expense = Expense.newExpense(
                    userId,
                    categoryId,
                    "Dinner",
                    Optional.empty(),
                    new Money(3000, CurrencyCode.of("USD")),
                    nanosecondInstant);

            Expense created = adapter.create(expense);

            Instant truncated = nanosecondInstant.truncatedTo(ChronoUnit.MICROS);
            List<ExpenseEntity> rows = expenseRowsFor(userId);
            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.createdAt()).isEqualTo(truncated);
                assertThat(row.updatedAt()).isEqualTo(truncated);
            });
            assertThat(created.createdAt()).isEqualTo(truncated);
            assertThat(created.updatedAt()).isEqualTo(truncated);
        }

        @Test
        @DisplayName(
                "when called with a description exactly 500 characters long - then the row is written and carries the whole description")
        void whenDescriptionIsExactly500Characters_thenRowIsWrittenAndCarriesWholeDescription() {
            long userId = storedUserId("boundary-description-user");
            long categoryId = storedGroupingId(userId, "Boundary");
            String description = "a".repeat(500);
            Expense expense = Expense.newExpense(
                    userId,
                    categoryId,
                    description,
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            adapter.create(expense);

            List<ExpenseEntity> rows = expenseRowsFor(userId);
            assertThat(rows)
                    .singleElement()
                    .satisfies(row -> assertThat(row.description()).hasSize(500).isEqualTo(description));
        }

        @Test
        @DisplayName(
                "when called with a description 501 characters long - then throws InvalidExpenseException before anything is written, so no expense row exists afterwards")
        void whenDescriptionIs501Characters_thenThrowsInvalidExpenseExceptionBeforeWritingAnything() {
            long userId = storedUserId("overlong-description-user");
            long categoryId = storedGroupingId(userId, "Boundary");
            String description = "a".repeat(501);
            Expense expense = Expense.newExpense(
                    userId,
                    categoryId,
                    description,
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            assertThatThrownBy(() -> adapter.create(expense)).isInstanceOf(InvalidExpenseException.class);

            assertThat(expenseRowsFor(userId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with a merchant exactly 255 characters long - then the row is written and carries the whole merchant")
        void whenMerchantIsExactly255Characters_thenRowIsWrittenAndCarriesWholeMerchant() {
            long userId = storedUserId("boundary-merchant-user");
            long categoryId = storedGroupingId(userId, "Boundary");
            String merchant = "a".repeat(255);
            Expense expense = Expense.newExpense(
                    userId,
                    categoryId,
                    "Purchase",
                    Optional.of(merchant),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            adapter.create(expense);

            List<ExpenseEntity> rows = expenseRowsFor(userId);
            assertThat(rows)
                    .singleElement()
                    .satisfies(row -> assertThat(row.merchant()).hasSize(255).isEqualTo(merchant));
        }

        @Test
        @DisplayName(
                "when called with a merchant 256 characters long - then throws InvalidExpenseException before anything is written")
        void whenMerchantIs256Characters_thenThrowsInvalidExpenseExceptionBeforeWritingAnything() {
            long userId = storedUserId("overlong-merchant-user");
            long categoryId = storedGroupingId(userId, "Boundary");
            String merchant = "a".repeat(256);
            Expense expense = Expense.newExpense(
                    userId,
                    categoryId,
                    "Purchase",
                    Optional.of(merchant),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            assertThatThrownBy(() -> adapter.create(expense)).isInstanceOf(InvalidExpenseException.class);

            assertThat(expenseRowsFor(userId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with an expense whose category id is positive and names no stored category - then throws EntityNotFoundException whose entityType() is \"category\", not PersistenceFailedException")
        void whenCategoryIdNamesNoStoredCategory_thenThrowsEntityNotFoundExceptionForCategory() {
            long userId = storedUserId("unknown-category-user");
            long unknownCategoryId = 999_999_999L;
            Expense expense = Expense.newExpense(
                    userId,
                    unknownCategoryId,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> adapter.create(expense))
                    .extracting(EntityNotFoundException::entityType)
                    .isEqualTo("category");
        }

        @Test
        @DisplayName(
                "when called with an expense whose user id is positive and names no stored user - then throws EntityNotFoundException whose entityType() is \"user\"")
        void whenUserIdNamesNoStoredUser_thenThrowsEntityNotFoundExceptionForUser() {
            long unknownUserId = 999_999_999L;
            long categoryId = storedGroupingId(storedUserId("category-owner-for-unknown-user"), "Category");
            Expense expense = Expense.newExpense(
                    unknownUserId,
                    categoryId,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> adapter.create(expense))
                    .extracting(EntityNotFoundException::entityType)
                    .isEqualTo("user");
        }

        @Test
        @DisplayName(
                "when called for two expenses of two different stored users, each with its own stored category - then each user owns exactly its own row, and neither references the other's")
        void whenCalledForTwoDifferentUsers_thenEachOwnsExactlyItsOwnRowWithNoCrossReference() {
            long firstUserId = storedUserId("first-expense-user");
            long firstCategoryId = storedGroupingId(firstUserId, "First Category");
            long secondUserId = storedUserId("second-expense-user");
            long secondCategoryId = storedGroupingId(secondUserId, "Second Category");

            Expense firstExpense = Expense.newExpense(
                    firstUserId,
                    firstCategoryId,
                    "First purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());
            Expense secondExpense = Expense.newExpense(
                    secondUserId,
                    secondCategoryId,
                    "Second purchase",
                    Optional.empty(),
                    new Money(200, CurrencyCode.of("USD")),
                    Instant.now());

            adapter.create(firstExpense);
            adapter.create(secondExpense);

            List<ExpenseEntity> firstUserRows = expenseRowsFor(firstUserId);
            List<ExpenseEntity> secondUserRows = expenseRowsFor(secondUserId);
            assertThat(firstUserRows).singleElement().satisfies(row -> {
                assertThat(row.userId()).isEqualTo(firstUserId);
                assertThat(row.categoryId()).isEqualTo(firstCategoryId);
            });
            assertThat(secondUserRows).singleElement().satisfies(row -> {
                assertThat(row.userId()).isEqualTo(secondUserId);
                assertThat(row.categoryId()).isEqualTo(secondCategoryId);
            });
        }
    }

    @Nested
    @DisplayName("counting expenses by message reference")
    class CountByMessageReference {

        @Test
        @DisplayName("when two expenses share a reference and one does not - then returns 2")
        void whenTwoExpensesStoredUnderReferenceAndOneUnderAnother_thenReturnsTwo() {
            long userId = storedUserId("count-two-expenses-user");
            long categoryId = storedGroupingId(userId, "Groceries");
            MessageReference reference = MessageReference.newReference();
            MessageReference otherReference = MessageReference.newReference();
            storedExpense(userId, categoryId, "First", 100, "USD", reference.value());
            storedExpense(userId, categoryId, "Second", 200, "USD", reference.value());
            storedExpense(userId, categoryId, "Other reference", 300, "USD", otherReference.value());

            int count = adapter.countByMessageReference(userId, reference);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("when nothing was stored under the reference - then returns 0")
        void whenReferenceHasNoStoredExpenses_thenReturnsZero() {
            long userId = storedUserId("count-no-expenses-user");

            int count = adapter.countByMessageReference(userId, MessageReference.newReference());

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("when every expense row carries a null message_reference - then returns 0")
        void whenAllExpensesHaveNullMessageReference_thenReturnsZero() {
            long userId = storedUserId("count-null-reference-user");
            long categoryId = storedGroupingId(userId, "Groceries");
            storedExpense(userId, categoryId, "No message", 100, "USD", null);

            int count = adapter.countByMessageReference(userId, MessageReference.newReference());

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("when two users share a reference value - then counting for one ignores the other's expense")
        void whenTwoUsersShareReferenceValue_thenReturnsOne() {
            long firstUserId = storedUserId("count-shared-reference-first-user");
            long firstCategoryId = storedGroupingId(firstUserId, "Groceries");
            long secondUserId = storedUserId("count-shared-reference-second-user");
            long secondCategoryId = storedGroupingId(secondUserId, "Groceries");
            MessageReference sharedReference = MessageReference.newReference();
            storedExpense(firstUserId, firstCategoryId, "First user's expense", 100, "USD", sharedReference.value());
            storedExpense(secondUserId, secondCategoryId, "Second user's expense", 200, "USD", sharedReference.value());

            int count = adapter.countByMessageReference(firstUserId, sharedReference);

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("totaling expenses by currency over a period")
    class TotalsByCurrency {

        @Test
        @DisplayName(
                "when a stored user has four EUR expenses and one HUF expense inside the period - then returns one total per currency, ordered by currency code, with no currency added to another")
        void whenFourEurExpensesAndOneHufExpenseInsidePeriod_thenReturnsOneTotalPerCurrencyOrderedByCode() {
            long userId = storedUserId("totals-mixed-currency-user");
            long categoryId = storedGroupingId(userId, "Groceries");
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
            Instant insidePeriod =
                    period.from().atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
            storedExpenseAt(userId, categoryId, "EUR one", 1000, "EUR", insidePeriod);
            storedExpenseAt(userId, categoryId, "EUR two", 2000, "EUR", insidePeriod);
            storedExpenseAt(userId, categoryId, "EUR three", 3000, "EUR", insidePeriod);
            storedExpenseAt(userId, categoryId, "EUR four", 4000, "EUR", insidePeriod);
            storedExpenseAt(userId, categoryId, "HUF one", 50000, "HUF", insidePeriod);

            List<CurrencyTotal> totals = adapter.totalsByCurrency(userId, period);

            assertThat(totals).hasSize(2);
            assertThat(totals.get(0).total()).isEqualTo(new Money(10000, CurrencyCode.of("EUR")));
            assertThat(totals.get(0).expenseCount()).isEqualTo(4);
            assertThat(totals.get(1).total()).isEqualTo(new Money(50000, CurrencyCode.of("HUF")));
            assertThat(totals.get(1).expenseCount()).isEqualTo(1);
        }

        @Test
        @DisplayName(
                "when expenses fall at the period's first day 00:00:00 UTC and last day 23:59:59 UTC - then both are counted")
        void whenExpensesFallOnFirstAndLastDayBounds_thenBothAreCounted() {
            long userId = storedUserId("totals-inclusive-bounds-user");
            long categoryId = storedGroupingId(userId, "Groceries");
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
            Instant firstDayMidnight =
                    period.from().atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant lastDayLastSecond =
                    period.to().atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
            storedExpenseAt(userId, categoryId, "First day midnight", 100, "USD", firstDayMidnight);
            storedExpenseAt(userId, categoryId, "Last day last second", 200, "USD", lastDayLastSecond);

            List<CurrencyTotal> totals = adapter.totalsByCurrency(userId, period);

            assertThat(totals).singleElement().satisfies(total -> {
                assertThat(total.total()).isEqualTo(new Money(300, CurrencyCode.of("USD")));
                assertThat(total.expenseCount()).isEqualTo(2);
            });
        }

        @Test
        @DisplayName(
                "when expenses fall one microsecond before the period's first day and at 00:00:00 UTC on the day after its last - then neither is counted")
        void whenExpensesFallJustOutsidePeriodBounds_thenNeitherIsCounted() {
            long userId = storedUserId("totals-exclusive-bounds-user");
            long categoryId = storedGroupingId(userId, "Groceries");
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
            Instant justBeforeFirstDay =
                    period.from().atStartOfDay(ZoneOffset.UTC).toInstant().minus(1, ChronoUnit.MICROS);
            Instant dayAfterLastDayMidnight =
                    period.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            storedExpenseAt(userId, categoryId, "Just before period", 100, "USD", justBeforeFirstDay);
            storedExpenseAt(userId, categoryId, "Day after period", 200, "USD", dayAfterLastDayMidnight);

            List<CurrencyTotal> totals = adapter.totalsByCurrency(userId, period);

            assertThat(totals).isEmpty();
        }

        @Test
        @DisplayName(
                "when two stored users each have an expense inside the period - then only the requested user's expense is counted")
        void whenTwoUsersHaveExpensesInsidePeriod_thenOnlyRequestedUsersExpenseCounted() {
            long firstUserId = storedUserId("totals-two-users-first-user");
            long firstCategoryId = storedGroupingId(firstUserId, "Groceries");
            long secondUserId = storedUserId("totals-two-users-second-user");
            long secondCategoryId = storedGroupingId(secondUserId, "Groceries");
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
            Instant insidePeriod =
                    period.from().atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
            storedExpenseAt(firstUserId, firstCategoryId, "First user's expense", 100, "USD", insidePeriod);
            storedExpenseAt(secondUserId, secondCategoryId, "Second user's expense", 200, "USD", insidePeriod);

            List<CurrencyTotal> totals = adapter.totalsByCurrency(firstUserId, period);

            assertThat(totals).singleElement().satisfies(total -> {
                assertThat(total.total()).isEqualTo(new Money(100, CurrencyCode.of("USD")));
                assertThat(total.expenseCount()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName(
                "when an expense_proposal row exists inside the period and no expense row does - then returns an empty list")
        void whenOnlyProposalRowExistsInsidePeriod_thenReturnsEmptyList() {
            long userId = storedUserId("totals-only-proposal-user");
            long parentId = storedGroupingId(userId, "Food");
            long categoryId = CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, parentId, "Groceries");
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
            Instant insidePeriod =
                    period.from().atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Awaiting confirmation",
                    null,
                    500,
                    "USD",
                    MessageReference.newReference().value(),
                    insidePeriod);

            List<CurrencyTotal> totals = adapter.totalsByCurrency(userId, period);

            assertThat(totals).isEmpty();
        }

        @Test
        @DisplayName("when the user has no expenses in the period - then returns an empty list rather than throwing")
        void whenUserHasNoExpensesInPeriod_thenReturnsEmptyList() {
            long userId = storedUserId("totals-no-expenses-user");
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));

            List<CurrencyTotal> totals = adapter.totalsByCurrency(userId, period);

            assertThat(totals).isEmpty();
        }
    }

    @Nested
    @DisplayName("finding a page of expenses and proposals")
    class FindPage {

        @Test
        @DisplayName(
                "when called with an unnarrowed filter - then both kinds come back in one list, newest first, each carrying the status of the table it came from")
        void whenCalledWithUnnarrowedFilter_thenBothKindsComeBackNewestFirstCarryingSourceTableStatus() {
            long userId = storedUserId("find-page-both-kinds-user");
            long categoryId = storedGroupingId(userId, "Groceries");
            Instant earlier = Instant.now().minusSeconds(120);
            Instant later = Instant.now().minusSeconds(60);
            storedExpenseAt(userId, categoryId, "Recorded expense", 100, "USD", earlier);
            storedProposalAt(userId, categoryId, "Pending proposal", 200, "USD", later);

            List<ExpenseEntry> page = adapter.findPage(userId, unnarrowedFilter());

            assertThat(page).hasSize(2);
            assertThat(page.get(0).status()).isEqualTo(ExpenseStatus.PENDING);
            assertThat(page.get(0).description()).isEqualTo("Pending proposal");
            assertThat(page.get(1).status()).isEqualTo(ExpenseStatus.RECORDED);
            assertThat(page.get(1).description()).isEqualTo("Recorded expense");
        }

        @Test
        @DisplayName(
                "when called with a status of PENDING, and separately of RECORDED - then only that kind comes back each time")
        void whenCalledWithEachStatus_thenOnlyThatKindComesBackEachTime() {
            long userId = storedUserId("find-page-status-filter-user");
            long categoryId = storedGroupingId(userId, "Dining");
            storedExpenseAt(
                    userId, categoryId, "Recorded", 100, "USD", Instant.now().minusSeconds(60));
            storedProposalAt(
                    userId, categoryId, "Pending", 200, "USD", Instant.now().minusSeconds(30));

            List<ExpenseEntry> pending = adapter.findPage(
                    userId, new ExpenseFilter(ExpenseStatus.PENDING, null, null, ExpenseFilter.DEFAULT_LIMIT, 0));
            List<ExpenseEntry> recorded = adapter.findPage(
                    userId, new ExpenseFilter(ExpenseStatus.RECORDED, null, null, ExpenseFilter.DEFAULT_LIMIT, 0));

            assertThat(pending).singleElement().satisfies(entry -> assertThat(entry.status())
                    .isEqualTo(ExpenseStatus.PENDING));
            assertThat(recorded).singleElement().satisfies(entry -> assertThat(entry.status())
                    .isEqualTo(ExpenseStatus.RECORDED));
        }

        @Test
        @DisplayName(
                "when called with a from and to spanning some rows and excluding others - then only the rows inside come back, the last day included, the boundary taken at UTC")
        void whenCalledWithDateRange_thenOnlyRowsInsideComeBackWithLastDayIncludedAtUtcBoundary() {
            long userId = storedUserId("find-page-date-range-user");
            long categoryId = storedGroupingId(userId, "Travel");
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
            Instant firstDayMidnight =
                    period.from().atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant lastDayLastSecond =
                    period.to().atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();
            Instant justBeforeFirstDay = firstDayMidnight.minus(1, ChronoUnit.MICROS);
            Instant dayAfterLastDayMidnight =
                    period.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            storedExpenseAt(userId, categoryId, "First day", 100, "USD", firstDayMidnight);
            storedExpenseAt(userId, categoryId, "Last day", 200, "USD", lastDayLastSecond);
            storedExpenseAt(userId, categoryId, "Just before", 300, "USD", justBeforeFirstDay);
            storedExpenseAt(userId, categoryId, "Day after", 400, "USD", dayAfterLastDayMidnight);

            List<ExpenseEntry> page =
                    adapter.findPage(userId, new ExpenseFilter(null, null, period, ExpenseFilter.DEFAULT_LIMIT, 0));

            assertThat(page).extracting(ExpenseEntry::description).containsExactlyInAnyOrder("First day", "Last day");
        }

        @Test
        @DisplayName(
                "when called with a limit, then again with the same limit and an offset of one page - then the second page continues the first and repeats no row from it")
        void whenCalledWithLimitThenSameLimitWithOffsetOfOnePage_thenSecondPageContinuesFirstRepeatingNoRow() {
            long userId = storedUserId("find-page-pagination-user");
            long categoryId = storedGroupingId(userId, "Shopping");
            Instant base = Instant.now().minusSeconds(300);
            storedExpenseAt(userId, categoryId, "First", 100, "USD", base);
            storedExpenseAt(userId, categoryId, "Second", 200, "USD", base.plusSeconds(60));
            storedExpenseAt(userId, categoryId, "Third", 300, "USD", base.plusSeconds(120));

            List<ExpenseEntry> firstPage = adapter.findPage(userId, new ExpenseFilter(null, null, null, 2, 0));
            List<ExpenseEntry> secondPage = adapter.findPage(userId, new ExpenseFilter(null, null, null, 2, 2));

            assertThat(firstPage).hasSize(2);
            assertThat(secondPage).hasSize(1);
            List<Long> firstIds = firstPage.stream().map(ExpenseEntry::id).toList();
            List<Long> secondIds = secondPage.stream().map(ExpenseEntry::id).toList();
            assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
        }

        @Test
        @DisplayName(
                "when called with an offset beyond the stored rows - then an empty list comes back rather than the last page again")
        void whenCalledWithOffsetBeyondStoredRows_thenEmptyListComesBackRatherThanLastPageAgain() {
            long userId = storedUserId("find-page-offset-overflow-user");
            long categoryId = storedGroupingId(userId, "Utilities");
            storedExpenseAt(userId, categoryId, "Only expense", 100, "USD", Instant.now());

            List<ExpenseEntry> withinRange = adapter.findPage(userId, unnarrowedFilter());
            List<ExpenseEntry> beyondRange =
                    adapter.findPage(userId, new ExpenseFilter(null, null, null, ExpenseFilter.DEFAULT_LIMIT, 10));

            assertThat(withinRange).hasSize(1);
            assertThat(beyondRange).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with a category id belonging to another user - then an empty list comes back, because every arm is scoped by the resolved user id")
        void whenCalledWithCategoryIdBelongingToAnotherUser_thenEmptyListComesBackScopedByResolvedUserId() {
            long firstUserId = storedUserId("find-page-cross-user-first-user");
            long secondUserId = storedUserId("find-page-cross-user-second-user");
            long secondUsersCategoryId = storedGroupingId(secondUserId, "Second User Category");
            storedExpenseAt(secondUserId, secondUsersCategoryId, "Second user's expense", 100, "USD", Instant.now());
            storedExpenseAt(
                    firstUserId,
                    storedGroupingId(firstUserId, "First User Category"),
                    "First user's expense",
                    200,
                    "USD",
                    Instant.now());

            List<ExpenseEntry> forSecondUsersOwnCategory = adapter.findPage(
                    secondUserId, new ExpenseFilter(null, secondUsersCategoryId, null, ExpenseFilter.DEFAULT_LIMIT, 0));
            List<ExpenseEntry> forFirstUserWithSecondUsersCategory = adapter.findPage(
                    firstUserId, new ExpenseFilter(null, secondUsersCategoryId, null, ExpenseFilter.DEFAULT_LIMIT, 0));

            assertThat(forSecondUsersOwnCategory).hasSize(1);
            assertThat(forFirstUserWithSecondUsersCategory).isEmpty();
        }

        @Test
        @DisplayName(
                "when two rows share a created_at, one in each table - then the order between them is the same on every call, so a page boundary is deterministic")
        void whenTwoRowsShareCreatedAtOneInEachTable_thenOrderIsTheSameOnEveryCall() {
            long userId = storedUserId("find-page-tie-break-user");
            long categoryId = storedGroupingId(userId, "Entertainment");
            Instant sharedInstant = Instant.now().minusSeconds(10);
            storedExpenseAt(userId, categoryId, "Recorded tie", 100, "USD", sharedInstant);
            storedProposalAt(userId, categoryId, "Pending tie", 200, "USD", sharedInstant);

            List<ExpenseEntry> firstCall = adapter.findPage(userId, unnarrowedFilter());
            List<ExpenseEntry> secondCall = adapter.findPage(userId, unnarrowedFilter());

            assertThat(firstCall).hasSize(2);
            assertThat(firstCall.stream().map(ExpenseEntry::id).toList())
                    .isEqualTo(secondCall.stream().map(ExpenseEntry::id).toList());
        }
    }

    @Nested
    @DisplayName("counting expenses and proposals matching a filter")
    class CountMatching {

        @Test
        @DisplayName(
                "when called with an unnarrowed filter - then the answer is every row the user has, across both tables")
        void whenCalledWithUnnarrowedFilter_thenAnswerIsEveryRowAcrossBothTables() {
            long userId = storedUserId("count-matching-unnarrowed-user");
            long categoryId = storedGroupingId(userId, "Groceries");
            storedExpenseAt(userId, categoryId, "First", 100, "USD", Instant.now());
            storedExpenseAt(userId, categoryId, "Second", 200, "USD", Instant.now());
            storedProposalAt(userId, categoryId, "Third", 300, "USD", Instant.now());

            long count = adapter.countMatching(userId, unnarrowedFilter());

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("when called with a status of PENDING - then only the proposals are counted")
        void whenCalledWithStatusPending_thenOnlyProposalsAreCounted() {
            long userId = storedUserId("count-matching-status-user");
            long categoryId = storedGroupingId(userId, "Dining");
            storedExpenseAt(userId, categoryId, "Recorded one", 100, "USD", Instant.now());
            storedExpenseAt(userId, categoryId, "Recorded two", 200, "USD", Instant.now());
            storedProposalAt(userId, categoryId, "Pending one", 300, "USD", Instant.now());

            long count = adapter.countMatching(
                    userId, new ExpenseFilter(ExpenseStatus.PENDING, null, null, ExpenseFilter.DEFAULT_LIMIT, 0));

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName(
                "when called with a filter whose limit and offset would return one page - then the answer ignores the limit and the offset, so a page can say how many rows the filter matches")
        void whenFilterCarriesLimitAndOffset_thenAnswerIgnoresThem() {
            long userId = storedUserId("count-matching-ignores-paging-user");
            long categoryId = storedGroupingId(userId, "Shopping");
            for (int i = 0; i < 5; i++) {
                storedExpenseAt(
                        userId,
                        categoryId,
                        "Expense " + i,
                        100,
                        "USD",
                        Instant.now().minusSeconds(i));
            }

            long count = adapter.countMatching(userId, new ExpenseFilter(null, null, null, 2, 3));

            assertThat(count).isEqualTo(5);
        }

        @Test
        @DisplayName("when called with a category id belonging to another user - then the answer is zero")
        void whenCalledWithCategoryIdBelongingToAnotherUser_thenAnswerIsZero() {
            long firstUserId = storedUserId("count-matching-cross-user-first-user");
            long secondUserId = storedUserId("count-matching-cross-user-second-user");
            long secondUsersCategoryId = storedGroupingId(secondUserId, "Second User Category");
            storedExpenseAt(secondUserId, secondUsersCategoryId, "Second user's expense", 100, "USD", Instant.now());

            long forSecondUsersOwnCategory = adapter.countMatching(
                    secondUserId, new ExpenseFilter(null, secondUsersCategoryId, null, ExpenseFilter.DEFAULT_LIMIT, 0));
            long forFirstUserWithSecondUsersCategory = adapter.countMatching(
                    firstUserId, new ExpenseFilter(null, secondUsersCategoryId, null, ExpenseFilter.DEFAULT_LIMIT, 0));

            assertThat(forSecondUsersOwnCategory).isEqualTo(1);
            assertThat(forFirstUserWithSecondUsersCategory).isEqualTo(0);
        }
    }

    // The scenario below needs a store that misbehaves in a way the healthy containerized
    // Postgres cannot be made to: a non-constraint failure. It constructs its own adapter over a
    // Mockito mock and calls the adapter's own public method directly - it is still the adapter
    // under test, just not wired against the real database.
    @Nested
    @DisplayName("against a mocked store, not the containerized database")
    class WithAMockedStore {

        private final ExpenseEntityRepository mockedExpenseEntityRepository = mock(ExpenseEntityRepository.class);
        private final ExpenseRepositoryAdapter mockedAdapter =
                new ExpenseRepositoryAdapter(mockedExpenseEntityRepository);

        @Test
        @DisplayName(
                "when create() hits a database failure that is not a constraint violation - then throws PersistenceFailedException, not EntityNotFoundException, carrying the framework exception as its cause")
        void
                whenCreateHitsNonConstraintDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseEntityRepository.save(any())).thenThrow(frameworkException);
            Expense expense = Expense.newExpense(
                    1L, 1L, "Purchase", Optional.empty(), new Money(100, CurrencyCode.of("USD")), Instant.now());

            assertThatThrownBy(() -> mockedAdapter.create(expense))
                    .isInstanceOf(PersistenceFailedException.class)
                    .isNotInstanceOf(EntityNotFoundException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when create() hits a foreign key constraint violation naming neither of expense's own foreign keys - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenCreateHitsConstraintViolationNamingNeitherForeignKey_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            SQLException sqlException = new SQLException(
                    "ERROR: insert or update on table \"expense\" violates foreign key constraint \"some_other_table_fkey\"",
                    "23503");
            DataIntegrityViolationException frameworkException =
                    new DataIntegrityViolationException("constraint violation", sqlException);
            when(mockedExpenseEntityRepository.save(any())).thenThrow(frameworkException);
            Expense expense = Expense.newExpense(
                    1L, 1L, "Purchase", Optional.empty(), new Money(100, CurrencyCode.of("USD")), Instant.now());

            assertThatThrownBy(() -> mockedAdapter.create(expense))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when countByMessageReference() hits a database failure - then throws PersistenceFailedException, never EntityNotFoundException")
        void
                whenCountByMessageReferenceHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseEntityRepository.countByMessageReference(any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.countByMessageReference(1L, MessageReference.newReference()))
                    .isInstanceOf(PersistenceFailedException.class)
                    .isNotInstanceOf(EntityNotFoundException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when totalsByCurrency() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenTotalsByCurrencyHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseEntityRepository.totalsByCurrency(any(), any(), any()))
                    .thenThrow(frameworkException);
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));

            assertThatThrownBy(() -> mockedAdapter.totalsByCurrency(1L, period))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        // A default answer that throws for any call, rather than a stub on one method, so the
        // scenario stays about the failure surfacing and not about which query the adapter runs.
        @Test
        @DisplayName(
                "when findPage() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void whenFindPageHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            ExpenseEntityRepository throwingRepository = mock(ExpenseEntityRepository.class, invocation -> {
                throw frameworkException;
            });
            ExpenseRepositoryAdapter throwingAdapter = new ExpenseRepositoryAdapter(throwingRepository);
            ExpenseFilter filter = new ExpenseFilter(null, null, null, ExpenseFilter.DEFAULT_LIMIT, 0);

            assertThatThrownBy(() -> throwingAdapter.findPage(1L, filter))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when countMatching() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenCountMatchingHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            ExpenseEntityRepository throwingRepository = mock(ExpenseEntityRepository.class, invocation -> {
                throw frameworkException;
            });
            ExpenseRepositoryAdapter throwingAdapter = new ExpenseRepositoryAdapter(throwingRepository);
            ExpenseFilter filter = new ExpenseFilter(null, null, null, ExpenseFilter.DEFAULT_LIMIT, 0);

            assertThatThrownBy(() -> throwingAdapter.countMatching(1L, filter))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private long storedGroupingId(long userId, String name) {
        return CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, name);
    }

    private List<ExpenseEntity> expenseRowsFor(long userId) {
        return ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId);
    }

    // countByMessageReference's rows have to carry a message_reference, and totalsByCurrency's an
    // exact createdAt to probe the period's bounds - neither of which adapter.create() writes. So
    // both are seeded directly, the way ExpenseProposalRowUtils.storedProposal seeds a proposal row.
    private ExpenseEntity storedExpense(
            long userId,
            long categoryId,
            String description,
            long amountMinorUnits,
            String currencyCode,
            UUID messageReference) {
        return ExpenseRowUtils.storedExpense(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                description,
                null,
                amountMinorUnits,
                currencyCode,
                messageReference,
                Instant.now());
    }

    private ExpenseEntity storedExpenseAt(
            long userId,
            long categoryId,
            String description,
            long amountMinorUnits,
            String currencyCode,
            Instant createdAt) {
        return ExpenseRowUtils.storedExpense(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                description,
                null,
                amountMinorUnits,
                currencyCode,
                null,
                createdAt);
    }

    private ExpenseProposalEntity storedProposalAt(
            long userId,
            long categoryId,
            String description,
            long amountMinorUnits,
            String currencyCode,
            Instant createdAt) {
        return ExpenseProposalRowUtils.storedProposal(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                description,
                null,
                amountMinorUnits,
                currencyCode,
                MessageReference.newReference().value(),
                createdAt);
    }

    private ExpenseFilter unnarrowedFilter() {
        return new ExpenseFilter(null, null, null, ExpenseFilter.DEFAULT_LIMIT, 0);
    }
}
