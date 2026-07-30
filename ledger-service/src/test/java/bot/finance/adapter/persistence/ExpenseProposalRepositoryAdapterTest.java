package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.common.CategoryRowUtils;
import bot.finance.common.ExpenseProposalRowUtils;
import bot.finance.common.PersistenceAdapterTest;
import bot.finance.common.UserRowUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
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
@Import(ExpenseProposalRepositoryAdapter.class)
class ExpenseProposalRepositoryAdapterTest {

    @Autowired
    private ExpenseProposalRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("creating an expense proposal")
    class Create {

        @Test
        @DisplayName(
                "when called with a stored user, a stored category, and an unstored proposal carrying a merchant - then one proposal row exists for that user carrying the category id, description, merchant, minor units and currency code given, and the returned proposal carries its generated database id")
        void whenCalledWithMerchant_thenRowWrittenWithGivenFieldsAndReturnedProposalCarriesGeneratedId() {
            long userId = storedUserId("merchant-proposal-user");
            long categoryId = storedCategoryId(userId, "Groceries");
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Weekly shop",
                    Optional.of("Trader Joe's"),
                    new Money(1500, CurrencyCode.of("USD")),
                    Instant.now());

            ExpenseProposal created = adapter.create(proposal);

            assertThat(created.id()).isPresent();
            List<ExpenseProposalEntity> rows = expenseProposalRowsFor(userId);
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
                "when called with a stored user, a stored category, and an unstored proposal with no merchant - then the row's merchant column is null and the returned proposal's merchant is empty")
        void whenCalledWithNoMerchant_thenRowMerchantColumnIsNullAndReturnedProposalMerchantIsEmpty() {
            long userId = storedUserId("no-merchant-proposal-user");
            long categoryId = storedCategoryId(userId, "Utilities");
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Electric bill",
                    Optional.empty(),
                    new Money(4200, CurrencyCode.of("USD")),
                    Instant.now());

            ExpenseProposal created = adapter.create(proposal);

            assertThat(created.merchant()).isEmpty();
            List<ExpenseProposalEntity> rows = expenseProposalRowsFor(userId);
            assertThat(rows).singleElement().satisfies(row -> assertThat(row.merchant())
                    .isNull());
        }

        @Test
        @DisplayName(
                "when called with a proposal stamped with an instant carrying nanosecond precision and the row is read back - then both timestamps equal that instant truncated to microseconds")
        void whenInstantCarriesNanosecondPrecision_thenReadBackTimestampsAreTruncatedToMicroseconds() {
            long userId = storedUserId("nanosecond-proposal-user");
            long categoryId = storedCategoryId(userId, "Dining");
            Instant nanosecondInstant = Instant.parse("2026-01-15T10:30:00.123456789Z");
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Dinner",
                    Optional.empty(),
                    new Money(3000, CurrencyCode.of("USD")),
                    nanosecondInstant);

            ExpenseProposal created = adapter.create(proposal);

            Instant truncated = nanosecondInstant.truncatedTo(ChronoUnit.MICROS);
            List<ExpenseProposalEntity> rows = expenseProposalRowsFor(userId);
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
            long userId = storedUserId("boundary-description-proposal-user");
            long categoryId = storedCategoryId(userId, "Boundary");
            String description = "a".repeat(500);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    description,
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            adapter.create(proposal);

            List<ExpenseProposalEntity> rows = expenseProposalRowsFor(userId);
            assertThat(rows)
                    .singleElement()
                    .satisfies(row -> assertThat(row.description()).hasSize(500).isEqualTo(description));
        }

        @Test
        @DisplayName(
                "when called with a description 501 characters long - then throws InvalidExpenseProposalException before anything is written, so no proposal row exists afterwards")
        void whenDescriptionIs501Characters_thenThrowsInvalidExpenseProposalExceptionBeforeWritingAnything() {
            long userId = storedUserId("overlong-description-proposal-user");
            long categoryId = storedCategoryId(userId, "Boundary");
            String description = "a".repeat(501);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    description,
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            assertThatThrownBy(() -> adapter.create(proposal)).isInstanceOf(InvalidExpenseProposalException.class);

            assertThat(expenseProposalRowsFor(userId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with a merchant exactly 255 characters long - then the row is written and carries the whole merchant")
        void whenMerchantIsExactly255Characters_thenRowIsWrittenAndCarriesWholeMerchant() {
            long userId = storedUserId("boundary-merchant-proposal-user");
            long categoryId = storedCategoryId(userId, "Boundary");
            String merchant = "a".repeat(255);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Purchase",
                    Optional.of(merchant),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            adapter.create(proposal);

            List<ExpenseProposalEntity> rows = expenseProposalRowsFor(userId);
            assertThat(rows)
                    .singleElement()
                    .satisfies(row -> assertThat(row.merchant()).hasSize(255).isEqualTo(merchant));
        }

        @Test
        @DisplayName(
                "when called with a merchant 256 characters long - then throws InvalidExpenseProposalException before anything is written")
        void whenMerchantIs256Characters_thenThrowsInvalidExpenseProposalExceptionBeforeWritingAnything() {
            long userId = storedUserId("overlong-merchant-proposal-user");
            long categoryId = storedCategoryId(userId, "Boundary");
            String merchant = "a".repeat(256);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Purchase",
                    Optional.of(merchant),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            assertThatThrownBy(() -> adapter.create(proposal)).isInstanceOf(InvalidExpenseProposalException.class);

            assertThat(expenseProposalRowsFor(userId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with a proposal whose category id is positive and names no stored category - then throws EntityNotFoundException whose entityType() is \"category\", not PersistenceFailedException")
        void whenCategoryIdNamesNoStoredCategory_thenThrowsEntityNotFoundExceptionForCategory() {
            long userId = storedUserId("unknown-category-proposal-user");
            long unknownCategoryId = 999_999_999L;
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    unknownCategoryId,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> adapter.create(proposal))
                    .extracting(EntityNotFoundException::entityType)
                    .isEqualTo("category");
        }

        @Test
        @DisplayName(
                "when called with a proposal whose user id is positive and names no stored user - then throws EntityNotFoundException whose entityType() is \"user\"")
        void whenUserIdNamesNoStoredUser_thenThrowsEntityNotFoundExceptionForUser() {
            long unknownUserId = 999_999_999L;
            long categoryId = storedCategoryId(storedUserId("category-owner-for-unknown-proposal-user"), "Category");
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    unknownUserId,
                    categoryId,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> adapter.create(proposal))
                    .extracting(EntityNotFoundException::entityType)
                    .isEqualTo("user");
        }

        @Test
        @DisplayName(
                "when called for two proposals of two different stored users, each with its own stored category - then each user owns exactly its own row, and neither references the other's")
        void whenCalledForTwoDifferentUsers_thenEachOwnsExactlyItsOwnRowWithNoCrossReference() {
            long firstUserId = storedUserId("first-proposal-user");
            long firstCategoryId = storedCategoryId(firstUserId, "First Category");
            long secondUserId = storedUserId("second-proposal-user");
            long secondCategoryId = storedCategoryId(secondUserId, "Second Category");

            ExpenseProposal firstProposal = ExpenseProposal.newExpenseProposal(
                    firstUserId,
                    firstCategoryId,
                    "First purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    Instant.now());
            ExpenseProposal secondProposal = ExpenseProposal.newExpenseProposal(
                    secondUserId,
                    secondCategoryId,
                    "Second purchase",
                    Optional.empty(),
                    new Money(200, CurrencyCode.of("USD")),
                    Instant.now());

            adapter.create(firstProposal);
            adapter.create(secondProposal);

            List<ExpenseProposalEntity> firstUserRows = expenseProposalRowsFor(firstUserId);
            List<ExpenseProposalEntity> secondUserRows = expenseProposalRowsFor(secondUserId);
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

    // The scenarios below need a store that misbehaves in a way the healthy containerized
    // Postgres cannot be made to: a non-constraint failure, and a constraint failure naming
    // neither of expense_proposal's own foreign keys. They construct their own adapter over a
    // Mockito mock and call the adapter's own public method directly - it is still the adapter
    // under test, just not wired against the real database.
    @Nested
    @DisplayName("against a mocked store, not the containerized database")
    class WithAMockedStore {

        private final ExpenseProposalEntityRepository mockedExpenseProposalEntityRepository =
                mock(ExpenseProposalEntityRepository.class);
        private final ExpenseProposalRepositoryAdapter mockedAdapter =
                new ExpenseProposalRepositoryAdapter(mockedExpenseProposalEntityRepository);

        @Test
        @DisplayName(
                "when create() hits a database failure that is not a constraint violation - then throws PersistenceFailedException, not EntityNotFoundException, carrying the framework exception as its cause")
        void
                whenCreateHitsNonConstraintDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseProposalEntityRepository.save(any())).thenThrow(frameworkException);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    1L, 1L, "Purchase", Optional.empty(), new Money(100, CurrencyCode.of("USD")), Instant.now());

            assertThatThrownBy(() -> mockedAdapter.create(proposal))
                    .isInstanceOf(PersistenceFailedException.class)
                    .isNotInstanceOf(EntityNotFoundException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when create() hits a foreign key constraint violation naming neither of expense_proposal's own foreign keys - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenCreateHitsConstraintViolationNamingNeitherForeignKey_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            SQLException sqlException = new SQLException(
                    "ERROR: insert or update on table \"expense_proposal\" violates foreign key constraint \"some_other_table_fkey\"",
                    "23503");
            DataIntegrityViolationException frameworkException =
                    new DataIntegrityViolationException("constraint violation", sqlException);
            when(mockedExpenseProposalEntityRepository.save(any())).thenThrow(frameworkException);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    1L, 1L, "Purchase", Optional.empty(), new Money(100, CurrencyCode.of("USD")), Instant.now());

            assertThatThrownBy(() -> mockedAdapter.create(proposal))
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

    private List<ExpenseProposalEntity> expenseProposalRowsFor(long userId) {
        return ExpenseProposalRowUtils.expenseProposalRowsFor(jdbcAggregateTemplate, userId);
    }
}
