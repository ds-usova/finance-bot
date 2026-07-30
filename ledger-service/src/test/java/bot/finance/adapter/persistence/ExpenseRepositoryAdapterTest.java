package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.common.CategoryRowUtils;
import bot.finance.common.ExpenseRowUtils;
import bot.finance.common.PersistenceAdapterTest;
import bot.finance.common.UserRowUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
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
                "when called with a stored user, a stored category, and an unstored expense carrying a merchant - then one expense row exists for that user carrying the category id, description, merchant, minor units and currency code given, and the returned expense carries its generated database id")
        void whenCalledWithMerchant_thenRowWrittenWithGivenFieldsAndReturnedExpenseCarriesGeneratedId() {
            long userId = storedUserId("merchant-expense-user");
            long categoryId = storedCategoryId(userId, "Groceries");
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
            });
        }

        @Test
        @DisplayName(
                "when called with a stored user, a stored category, and an unstored expense with no merchant - then the row's merchant column is null and the returned expense's merchant is empty")
        void whenCalledWithNoMerchant_thenRowMerchantColumnIsNullAndReturnedExpenseMerchantIsEmpty() {
            long userId = storedUserId("no-merchant-expense-user");
            long categoryId = storedCategoryId(userId, "Utilities");
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
            long categoryId = storedCategoryId(userId, "Dining");
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
            long categoryId = storedCategoryId(userId, "Boundary");
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
            long categoryId = storedCategoryId(userId, "Boundary");
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
            long categoryId = storedCategoryId(userId, "Boundary");
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
            long categoryId = storedCategoryId(userId, "Boundary");
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
            long categoryId = storedCategoryId(storedUserId("category-owner-for-unknown-user"), "Category");
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
            long firstCategoryId = storedCategoryId(firstUserId, "First Category");
            long secondUserId = storedUserId("second-expense-user");
            long secondCategoryId = storedCategoryId(secondUserId, "Second Category");

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
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private long storedCategoryId(long userId, String name) {
        return CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, name);
    }

    private List<ExpenseEntity> expenseRowsFor(long userId) {
        return ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId);
    }
}
