package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ProposalSummary;
import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.ProposalIds;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
                "when called with a proposal carrying a merchant - then the row holds what was given and carries a generated id")
        void whenCalledWithMerchant_thenRowWrittenWithGivenFieldsAndReturnedProposalCarriesGeneratedId() {
            long userId = storedUserId("merchant-proposal-user");
            long categoryId = storedGroupingId(userId, "Groceries");
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Weekly shop",
                    Optional.of("Trader Joe's"),
                    new Money(1500, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
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
                "when the proposal carries no merchant - then the stored row and the returned proposal both carry none")
        void whenCalledWithNoMerchant_thenStoredRowAndReturnedProposalBothCarryNoMerchant() {
            long userId = storedUserId("no-merchant-proposal-user");
            long categoryId = storedGroupingId(userId, "Utilities");
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Electric bill",
                    Optional.empty(),
                    new Money(4200, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    Instant.now());

            ExpenseProposal created = adapter.create(proposal);

            assertThat(created.merchant()).isEmpty();
            List<ExpenseProposalEntity> rows = expenseProposalRowsFor(userId);
            assertThat(rows).singleElement().satisfies(row -> assertThat(row.merchant())
                    .isNull());
        }

        @Test
        @DisplayName(
                "when the proposal is stamped with nanosecond precision - then its timestamps are truncated to microseconds")
        void whenInstantCarriesNanosecondPrecision_thenTimestampsAreTruncatedToMicroseconds() {
            long userId = storedUserId("nanosecond-proposal-user");
            long categoryId = storedGroupingId(userId, "Dining");
            Instant nanosecondInstant = Instant.parse("2026-01-15T10:30:00.123456789Z");
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Dinner",
                    Optional.empty(),
                    new Money(3000, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
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
        @DisplayName("when the description is exactly 500 characters long - then the row carries the whole description")
        void whenDescriptionIsExactly500Characters_thenRowIsWrittenAndCarriesWholeDescription() {
            long userId = storedUserId("boundary-description-proposal-user");
            long categoryId = storedGroupingId(userId, "Boundary");
            String description = "a".repeat(500);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    description,
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    Instant.now());

            adapter.create(proposal);

            List<ExpenseProposalEntity> rows = expenseProposalRowsFor(userId);
            assertThat(rows)
                    .singleElement()
                    .satisfies(row -> assertThat(row.description()).hasSize(500).isEqualTo(description));
        }

        @Test
        @DisplayName(
                "when the description is 501 characters long - then throws InvalidExpenseProposalException and writes nothing")
        void whenDescriptionIs501Characters_thenThrowsInvalidExpenseProposalExceptionAndWritesNothing() {
            long userId = storedUserId("overlong-description-proposal-user");
            long categoryId = storedGroupingId(userId, "Boundary");
            String description = "a".repeat(501);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    description,
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    Instant.now());

            assertThatThrownBy(() -> adapter.create(proposal)).isInstanceOf(InvalidExpenseProposalException.class);

            assertThat(expenseProposalRowsFor(userId)).isEmpty();
        }

        @Test
        @DisplayName("when the merchant is exactly 255 characters long - then the row carries the whole merchant")
        void whenMerchantIsExactly255Characters_thenRowIsWrittenAndCarriesWholeMerchant() {
            long userId = storedUserId("boundary-merchant-proposal-user");
            long categoryId = storedGroupingId(userId, "Boundary");
            String merchant = "a".repeat(255);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Purchase",
                    Optional.of(merchant),
                    new Money(100, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    Instant.now());

            adapter.create(proposal);

            List<ExpenseProposalEntity> rows = expenseProposalRowsFor(userId);
            assertThat(rows)
                    .singleElement()
                    .satisfies(row -> assertThat(row.merchant()).hasSize(255).isEqualTo(merchant));
        }

        @Test
        @DisplayName(
                "when the merchant is 256 characters long - then throws InvalidExpenseProposalException and writes nothing")
        void whenMerchantIs256Characters_thenThrowsInvalidExpenseProposalExceptionAndWritesNothing() {
            long userId = storedUserId("overlong-merchant-proposal-user");
            long categoryId = storedGroupingId(userId, "Boundary");
            String merchant = "a".repeat(256);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Purchase",
                    Optional.of(merchant),
                    new Money(100, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    Instant.now());

            assertThatThrownBy(() -> adapter.create(proposal)).isInstanceOf(InvalidExpenseProposalException.class);

            assertThat(expenseProposalRowsFor(userId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when the category id names no stored category - then throws EntityNotFoundException for the category")
        void whenCategoryIdNamesNoStoredCategory_thenThrowsEntityNotFoundExceptionForCategory() {
            long userId = storedUserId("unknown-category-proposal-user");
            long unknownCategoryId = 999_999_999L;
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    unknownCategoryId,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    Instant.now());

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> adapter.create(proposal))
                    .extracting(EntityNotFoundException::entityType)
                    .isEqualTo("category");
        }

        @Test
        @DisplayName("when the user id names no stored user - then throws EntityNotFoundException for the user")
        void whenUserIdNamesNoStoredUser_thenThrowsEntityNotFoundExceptionForUser() {
            long unknownUserId = 999_999_999L;
            long categoryId = storedGroupingId(storedUserId("category-owner-for-unknown-proposal-user"), "Category");
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    unknownUserId,
                    categoryId,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    Instant.now());

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> adapter.create(proposal))
                    .extracting(EntityNotFoundException::entityType)
                    .isEqualTo("user");
        }

        @Test
        @DisplayName("when two users each have a proposal created - then each owns exactly its own row")
        void whenCalledForTwoDifferentUsers_thenEachOwnsExactlyItsOwnRow() {
            long firstUserId = storedUserId("first-proposal-user");
            long firstCategoryId = storedGroupingId(firstUserId, "First Category");
            long secondUserId = storedUserId("second-proposal-user");
            long secondCategoryId = storedGroupingId(secondUserId, "Second Category");

            ExpenseProposal firstProposal = ExpenseProposal.newExpenseProposal(
                    firstUserId,
                    firstCategoryId,
                    "First purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    Instant.now());
            ExpenseProposal secondProposal = ExpenseProposal.newExpenseProposal(
                    secondUserId,
                    secondCategoryId,
                    "Second purchase",
                    Optional.empty(),
                    new Money(200, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
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

        @Test
        @DisplayName(
                "when the proposal carries a message reference - then the row's message_reference column holds its UUID")
        void whenCalledWithMessageReference_thenRowMessageReferenceColumnHoldsItsUuid() {
            long userId = storedUserId("message-reference-proposal-user");
            long categoryId = storedGroupingId(userId, "Category");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    reference,
                    Instant.now());

            adapter.create(proposal);

            List<ExpenseProposalEntity> rows = expenseProposalRowsFor(userId);
            assertThat(rows).singleElement().satisfies(row -> assertThat(row.incomingMessageId())
                    .isEqualTo(reference.value()));
        }
    }

    @Nested
    @DisplayName("finding proposal summaries by message reference")
    class FindSummariesByMessageReference {

        @Test
        @DisplayName(
                "when three proposals were stored under one reference - then returns three fully mapped summaries, oldest first")
        void whenThreeProposalsStoredUnderSameReference_thenReturnsThreeSummariesOldestFirstWithFullMapping() {
            long userId = storedUserId("summary-ordering-user");
            long parentId = storedGroupingId(userId, "Food");
            long categoryId = storedCategoryId(userId, parentId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            Instant base = Instant.now().minusSeconds(60);

            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Third",
                    "Merchant Three",
                    300,
                    "USD",
                    reference.value(),
                    base.plusSeconds(20));
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "First",
                    "Merchant One",
                    100,
                    "USD",
                    reference.value(),
                    base);
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Second",
                    "Merchant Two",
                    200,
                    "USD",
                    reference.value(),
                    base.plusSeconds(10));

            List<ProposalSummary> summaries = adapter.findSummariesByMessageReference(userId, reference);

            assertThat(summaries).hasSize(3);
            assertThat(summaries.get(0)).satisfies(summary -> {
                assertThat(summary.categoryName()).isEqualTo("Groceries");
                assertThat(summary.groupingName()).isEqualTo("Food");
                assertThat(summary.description()).isEqualTo("First");
                assertThat(summary.merchant()).contains("Merchant One");
                assertThat(summary.money()).isEqualTo(new Money(100, CurrencyCode.of("USD")));
            });
            assertThat(summaries.get(1).description()).isEqualTo("Second");
            assertThat(summaries.get(2).description()).isEqualTo("Third");
        }

        @Test
        @DisplayName(
                "when a user's proposals were stored under two references - then only the one asked for comes back")
        void whenUserHasProposalsUnderTwoReferences_thenOnlyTheOneAskedForComesBack() {
            long userId = storedUserId("summary-two-references-user");
            long parentId = storedGroupingId(userId, "Food");
            long categoryId = storedCategoryId(userId, parentId, "Groceries");
            IncomingMessageId firstReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            IncomingMessageId secondReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Under first reference",
                    null,
                    100,
                    "USD",
                    firstReference.value(),
                    Instant.now());
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Under second reference",
                    null,
                    200,
                    "USD",
                    secondReference.value(),
                    Instant.now());

            List<ProposalSummary> summaries = adapter.findSummariesByMessageReference(userId, firstReference);

            assertThat(summaries).singleElement().satisfies(summary -> assertThat(summary.description())
                    .isEqualTo("Under first reference"));
        }

        @Test
        @DisplayName(
                "when called with one user's id and a reference value two stored users share - then only that user's proposals come back")
        void whenTwoUsersShareAReferenceValue_thenOnlyRequestedUsersProposalsComeBack() {
            long firstUserId = storedUserId("summary-shared-reference-first-user");
            long firstCategoryId = storedCategoryId(firstUserId, storedGroupingId(firstUserId, "Food"), "Groceries");
            long secondUserId = storedUserId("summary-shared-reference-second-user");
            long secondCategoryId = storedCategoryId(secondUserId, storedGroupingId(secondUserId, "Food"), "Groceries");
            IncomingMessageId sharedReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    firstUserId,
                    firstCategoryId,
                    "First user's proposal",
                    null,
                    100,
                    "USD",
                    sharedReference.value(),
                    Instant.now());
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    secondUserId,
                    secondCategoryId,
                    "Second user's proposal",
                    null,
                    200,
                    "USD",
                    sharedReference.value(),
                    Instant.now());

            List<ProposalSummary> summaries = adapter.findSummariesByMessageReference(firstUserId, sharedReference);

            assertThat(summaries).singleElement().satisfies(summary -> assertThat(summary.description())
                    .isEqualTo("First user's proposal"));
        }

        @Test
        @DisplayName(
                "when called with a stored user and a reference nothing was written under - then returns an empty list")
        void whenReferenceHasNoStoredProposals_thenReturnsEmptyList() {
            long userId = storedUserId("summary-no-proposals-user");

            List<ProposalSummary> summaries = adapter.findSummariesByMessageReference(
                    userId, IncomingMessageId.of(UUID.randomUUID().toString()));

            assertThat(summaries).isEmpty();
        }

        @Test
        @DisplayName(
                "when a stored proposal's merchant column is null - then that summary's merchant is Optional.empty()")
        void whenStoredProposalsMerchantColumnIsNull_thenSummaryMerchantIsEmpty() {
            long userId = storedUserId("summary-no-merchant-user");
            long parentId = storedGroupingId(userId, "Food");
            long categoryId = storedCategoryId(userId, parentId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "No merchant",
                    null,
                    100,
                    "USD",
                    reference.value(),
                    Instant.now());

            List<ProposalSummary> summaries = adapter.findSummariesByMessageReference(userId, reference);

            assertThat(summaries).singleElement().satisfies(summary -> assertThat(summary.merchant())
                    .isEmpty());
        }
    }

    @Nested
    @DisplayName("accepting proposals under a message reference")
    class Accept {

        @Test
        @DisplayName("when two proposals share a reference and a third does not - then only those two move to expense")
        void whenTwoProposalsShareAReference_thenOnlyThoseMoveToExpense() {
            long userId = storedUserId("accept-two-proposals-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            IncomingMessageId otherReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            // An exact microsecond: Postgres rounds a finer instant to the nearest one, so a fixture carrying
            // nanoseconds cannot be compared against what comes back.
            Instant createdAt = Instant.parse("2026-01-12T18:04:00Z");
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "With merchant",
                    "Trader Joe's",
                    1500,
                    "USD",
                    reference.value(),
                    createdAt);
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "No merchant",
                    null,
                    2500,
                    "EUR",
                    reference.value(),
                    createdAt);
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Other reference",
                    null,
                    500,
                    "USD",
                    otherReference.value(),
                    createdAt);
            Instant now = Instant.now();

            int moved = adapter.accept(userId, reference, now);

            assertThat(moved).isEqualTo(2);
            Instant truncatedNow = now.truncatedTo(ChronoUnit.MICROS);
            List<ExpenseEntity> expenseRows = expenseRowsFor(userId);
            assertThat(expenseRows).hasSize(2);
            assertThat(expenseRows)
                    .anySatisfy(row -> {
                        assertThat(row.categoryId()).isEqualTo(categoryId);
                        assertThat(row.description()).isEqualTo("With merchant");
                        assertThat(row.merchant()).isEqualTo("Trader Joe's");
                        assertThat(row.amountMinorUnits()).isEqualTo(1500);
                        assertThat(row.currencyCode()).isEqualTo("USD");
                        assertThat(row.incomingMessageId()).isEqualTo(reference.value());
                        assertThat(row.createdAt()).isEqualTo(createdAt);
                        assertThat(row.updatedAt()).isEqualTo(truncatedNow);
                    })
                    .anySatisfy(row -> {
                        assertThat(row.categoryId()).isEqualTo(categoryId);
                        assertThat(row.description()).isEqualTo("No merchant");
                        assertThat(row.merchant()).isNull();
                        assertThat(row.amountMinorUnits()).isEqualTo(2500);
                        assertThat(row.currencyCode()).isEqualTo("EUR");
                        assertThat(row.incomingMessageId()).isEqualTo(reference.value());
                        assertThat(row.createdAt()).isEqualTo(createdAt);
                        assertThat(row.updatedAt()).isEqualTo(truncatedNow);
                    });
            assertThat(expenseProposalRowsFor(userId)).singleElement().satisfies(row -> assertThat(row.description())
                    .isEqualTo("Other reference"));
        }

        @Test
        @DisplayName("when the stored proposal's merchant is null - then the written expense row's merchant is null")
        void whenProposalMerchantColumnIsNull_thenWrittenExpenseRowMerchantColumnIsNull() {
            long userId = storedUserId("accept-null-merchant-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Utilities");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Electric bill",
                    null,
                    4200,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(30));

            adapter.accept(userId, reference, Instant.now());

            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> assertThat(row.merchant())
                    .isNull());
        }

        @Test
        @DisplayName("when nothing was stored under the reference - then returns 0 and writes no expense row")
        void whenReferenceHasNoStoredProposals_thenReturnsZeroAndWritesNoExpenseRow() {
            long userId = storedUserId("accept-no-proposals-user");

            int moved = adapter.accept(
                    userId, IncomingMessageId.of(UUID.randomUUID().toString()), Instant.now());

            assertThat(moved).isEqualTo(0);
            assertThat(expenseRowsFor(userId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when two users share a reference value - then accepting for one leaves the other's proposal untouched")
        void whenTwoUsersShareReferenceValue_thenReturnsOneAndOnlyFirstUsersProposalMoves() {
            long firstUserId = storedUserId("accept-shared-reference-first-user");
            long firstCategoryId = storedCategoryId(firstUserId, storedGroupingId(firstUserId, "Food"), "Groceries");
            long secondUserId = storedUserId("accept-shared-reference-second-user");
            long secondCategoryId = storedCategoryId(secondUserId, storedGroupingId(secondUserId, "Food"), "Groceries");
            IncomingMessageId sharedReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    firstUserId,
                    firstCategoryId,
                    "First user's proposal",
                    null,
                    100,
                    "USD",
                    sharedReference.value(),
                    Instant.now().minusSeconds(30));
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    secondUserId,
                    secondCategoryId,
                    "Second user's proposal",
                    null,
                    200,
                    "USD",
                    sharedReference.value(),
                    Instant.now().minusSeconds(30));

            int moved = adapter.accept(firstUserId, sharedReference, Instant.now());

            assertThat(moved).isEqualTo(1);
            assertThat(expenseProposalRowsFor(firstUserId)).isEmpty();
            assertThat(expenseProposalRowsFor(secondUserId))
                    .singleElement()
                    .satisfies(row -> assertThat(row.description()).isEqualTo("Second user's proposal"));
        }

        @Test
        @DisplayName("when now carries nanosecond precision - then the written updated_at is truncated to microseconds")
        void whenNowCarriesNanosecondPrecision_thenWrittenTimestampsAreTruncatedToMicroseconds() {
            long userId = storedUserId("accept-nanosecond-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            Instant proposedAt = Instant.parse("2026-01-12T18:04:00Z");
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Dinner",
                    null,
                    3000,
                    "USD",
                    reference.value(),
                    proposedAt);
            Instant nanosecondInstant = Instant.parse("2026-01-15T10:30:00.123456789Z");

            adapter.accept(userId, reference, nanosecondInstant);

            Instant truncated = nanosecondInstant.truncatedTo(ChronoUnit.MICROS);
            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> {
                assertThat(row.createdAt()).isEqualTo(proposedAt);
                assertThat(row.updatedAt()).isEqualTo(truncated);
            });
        }
    }

    @Nested
    @DisplayName("discarding proposals under a message reference")
    class Discard {

        @Test
        @DisplayName("when two proposals share a reference and a third does not - then only those two are discarded")
        void whenTwoProposalsShareAReference_thenOnlyThoseTwoAreDiscarded() {
            long userId = storedUserId("discard-two-proposals-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            IncomingMessageId otherReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            Instant createdAt = Instant.now().minusSeconds(60);
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate, userId, categoryId, "First", null, 100, "USD", reference.value(), createdAt);
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Second",
                    null,
                    200,
                    "USD",
                    reference.value(),
                    createdAt);
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Other reference",
                    null,
                    300,
                    "USD",
                    otherReference.value(),
                    createdAt);

            int discarded = adapter.discard(userId, reference);

            assertThat(discarded).isEqualTo(2);
            assertThat(expenseProposalRowsFor(userId)).singleElement().satisfies(row -> assertThat(row.description())
                    .isEqualTo("Other reference"));
            assertThat(expenseRowsFor(userId)).isEmpty();
        }

        @Test
        @DisplayName("when called with a stored user and a reference nothing was written under - then returns 0")
        void whenReferenceHasNoStoredProposals_thenReturnsZero() {
            long userId = storedUserId("discard-no-proposals-user");

            int discarded = adapter.discard(
                    userId, IncomingMessageId.of(UUID.randomUUID().toString()));

            assertThat(discarded).isEqualTo(0);
        }

        @Test
        @DisplayName("when two users share a reference value - then discarding for one leaves the other's row intact")
        void whenTwoUsersShareReferenceValue_thenReturnsOneAndSecondUsersRowSurvives() {
            long firstUserId = storedUserId("discard-shared-reference-first-user");
            long firstCategoryId = storedCategoryId(firstUserId, storedGroupingId(firstUserId, "Food"), "Groceries");
            long secondUserId = storedUserId("discard-shared-reference-second-user");
            long secondCategoryId = storedCategoryId(secondUserId, storedGroupingId(secondUserId, "Food"), "Groceries");
            IncomingMessageId sharedReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    firstUserId,
                    firstCategoryId,
                    "First user's proposal",
                    null,
                    100,
                    "USD",
                    sharedReference.value(),
                    Instant.now().minusSeconds(30));
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    secondUserId,
                    secondCategoryId,
                    "Second user's proposal",
                    null,
                    200,
                    "USD",
                    sharedReference.value(),
                    Instant.now().minusSeconds(30));

            int discarded = adapter.discard(firstUserId, sharedReference);

            assertThat(discarded).isEqualTo(1);
            assertThat(expenseProposalRowsFor(secondUserId))
                    .singleElement()
                    .satisfies(row -> assertThat(row.description()).isEqualTo("Second user's proposal"));
        }
    }

    @Nested
    @DisplayName("accepting proposals by id")
    class AcceptByIds {

        @Test
        @DisplayName(
                "when two proposals share a reported message - then both move and the answer holds that message twice")
        void whenTwoProposalsShareReportedMessage_thenBothMoveAndAnswerHoldsMessageTwice() {
            long userId = storedUserId("accept-by-ids-two-on-one-message-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            Instant createdAt = Instant.now().minusSeconds(60);
            ExpenseProposalEntity first = ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "With merchant",
                    "Trader Joe's",
                    1500,
                    "USD",
                    reference.value(),
                    createdAt);
            ExpenseProposalEntity second = ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "No merchant",
                    null,
                    2500,
                    "EUR",
                    reference.value(),
                    createdAt);
            Instant now = Instant.now();

            List<IncomingMessageId> answer =
                    adapter.acceptByIds(userId, ProposalIds.of(List.of(first.id(), second.id())), now);

            assertThat(expenseProposalRowsFor(userId)).isEmpty();
            List<ExpenseEntity> expenseRows = expenseRowsFor(userId);
            assertThat(expenseRows).hasSize(2);
            assertThat(expenseRows)
                    .anySatisfy(row -> {
                        assertThat(row.categoryId()).isEqualTo(categoryId);
                        assertThat(row.description()).isEqualTo("With merchant");
                        assertThat(row.merchant()).isEqualTo("Trader Joe's");
                        assertThat(row.amountMinorUnits()).isEqualTo(1500);
                        assertThat(row.currencyCode()).isEqualTo("USD");
                        assertThat(row.incomingMessageId()).isEqualTo(reference.value());
                    })
                    .anySatisfy(row -> {
                        assertThat(row.categoryId()).isEqualTo(categoryId);
                        assertThat(row.description()).isEqualTo("No merchant");
                        assertThat(row.merchant()).isNull();
                        assertThat(row.amountMinorUnits()).isEqualTo(2500);
                        assertThat(row.currencyCode()).isEqualTo("EUR");
                        assertThat(row.incomingMessageId()).isEqualTo(reference.value());
                    });
            assertThat(answer).containsExactlyInAnyOrder(reference, reference);
        }

        @Test
        @DisplayName(
                "when a single pending proposal is accepted - then it moves and keeps the message it was reported on")
        void whenOnePendingProposalAcceptedAlone_thenItMovesAndKeepsReportedMessage() {
            long userId = storedUserId("accept-by-ids-single-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalEntity proposal = ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Dinner",
                    null,
                    3000,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(30));

            List<IncomingMessageId> answer =
                    adapter.acceptByIds(userId, ProposalIds.of(List.of(proposal.id())), Instant.now());

            assertThat(answer).containsExactly(reference);
            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> assertThat(row.incomingMessageId())
                    .isEqualTo(reference.value()));
        }

        @Test
        @DisplayName(
                "when two proposals reported on two different messages are accepted together - then the answer holds each message once")
        void whenTwoProposalsReportedOnTwoDifferentMessages_thenAnswerHoldsEachMessageOnce() {
            long userId = storedUserId("accept-by-ids-two-messages-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            IncomingMessageId firstReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            IncomingMessageId secondReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalEntity first = ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "First",
                    null,
                    100,
                    "USD",
                    firstReference.value(),
                    Instant.now().minusSeconds(30));
            ExpenseProposalEntity second = ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Second",
                    null,
                    200,
                    "USD",
                    secondReference.value(),
                    Instant.now().minusSeconds(30));

            List<IncomingMessageId> answer =
                    adapter.acceptByIds(userId, ProposalIds.of(List.of(first.id(), second.id())), Instant.now());

            assertThat(answer).containsExactlyInAnyOrder(firstReference, secondReference);
        }

        @Test
        @DisplayName(
                "when the same ids were accepted a moment earlier - then the answer is empty and no second expense row is stored")
        void whenSameIdsAcceptedAMomentEarlier_thenAnswerIsEmptyAndNoSecondExpenseRowStored() {
            long userId = storedUserId("accept-by-ids-repeat-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalEntity proposal = ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Dinner",
                    null,
                    3000,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(30));
            ProposalIds ids = ProposalIds.of(List.of(proposal.id()));
            adapter.acceptByIds(userId, ids, Instant.now());

            List<IncomingMessageId> secondAnswer = adapter.acceptByIds(userId, ids, Instant.now());

            assertThat(secondAnswer).isEmpty();
            assertThat(expenseRowsFor(userId)).hasSize(1);
        }

        @Test
        @DisplayName(
                "when an id names another person's pending proposal - then the answer is empty and that person's row still stands")
        void whenIdNamesAnotherPersonsProposal_thenAnswerIsEmptyAndOtherPersonsRowStillStands() {
            long ownerUserId = storedUserId("accept-by-ids-owner-user");
            long ownerCategoryId = storedCategoryId(ownerUserId, storedGroupingId(ownerUserId, "Food"), "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalEntity ownerProposal = ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    ownerUserId,
                    ownerCategoryId,
                    "Someone else's proposal",
                    null,
                    100,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(30));
            long callerUserId = storedUserId("accept-by-ids-caller-user");

            List<IncomingMessageId> answer =
                    adapter.acceptByIds(callerUserId, ProposalIds.of(List.of(ownerProposal.id())), Instant.now());

            assertThat(answer).isEmpty();
            assertThat(expenseProposalRowsFor(ownerUserId)).singleElement().satisfies(row -> assertThat(row.id())
                    .isEqualTo(ownerProposal.id()));
        }

        @Test
        @DisplayName(
                "when an id names the caller's own expense rather than a proposal - then nothing is accepted or written")
        void whenIdNamesCallersOwnExpenseRatherThanProposal_thenNothingIsAcceptedOrWritten() {
            long userId = storedUserId("accept-by-ids-names-expense-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseEntity existingExpense = ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Already recorded",
                    null,
                    500,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(30));

            List<IncomingMessageId> answer =
                    adapter.acceptByIds(userId, ProposalIds.of(List.of(existingExpense.id())), Instant.now());

            assertThat(answer).isEmpty();
            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> assertThat(row.id())
                    .isEqualTo(existingExpense.id()));
        }

        @Test
        @DisplayName("when accepting a proposal made days ago - then the expense keeps that day and is updated now")
        void whenAcceptingAProposalMadeDaysAgo_thenExpenseKeepsThatDayAndIsUpdatedNow() {
            long userId = storedUserId("accept-by-ids-timestamp-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            Instant proposedAt = Instant.parse("2026-01-12T18:04:00Z");
            ExpenseProposalEntity proposal = ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Dinner",
                    null,
                    3000,
                    "USD",
                    reference.value(),
                    proposedAt);
            Instant now = Instant.parse("2026-01-15T10:30:00.123456789Z");

            adapter.acceptByIds(userId, ProposalIds.of(List.of(proposal.id())), now);

            // The day a person sees the entry under is created_at, so re-dating it on acceptance would move a
            // days-old proposal to today and empty the day it was made on.
            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> {
                assertThat(row.createdAt()).isEqualTo(proposedAt);
                assertThat(row.updatedAt()).isEqualTo(now.truncatedTo(ChronoUnit.MICROS));
            });
        }
    }

    @Nested
    @DisplayName("finding which messages still hold a pending proposal")
    class FindWithPendingProposals {

        @Test
        @DisplayName("when only one of two messages still holds a pending proposal - then only that one is answered")
        void whenOnlyOneOfTwoMessagesStillHoldsPendingProposal_thenOnlyThatOneIsAnswered() {
            long userId = storedUserId("pending-messages-one-empty-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            IncomingMessageId stillPending =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            IncomingMessageId emptied = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Still pending",
                    null,
                    100,
                    "USD",
                    stillPending.value(),
                    Instant.now().minusSeconds(30));

            Set<IncomingMessageId> answer = adapter.findWithPendingProposals(userId, List.of(stillPending, emptied));

            assertThat(answer).containsExactly(stillPending);
        }

        @Test
        @DisplayName(
                "when a message's only remaining proposal belongs to another person - then it is not answered for the caller")
        void whenMessagesOnlyRemainingProposalBelongsToAnotherPerson_thenNotAnsweredForCaller() {
            long ownerUserId = storedUserId("pending-messages-other-owner-user");
            long ownerCategoryId = storedCategoryId(ownerUserId, storedGroupingId(ownerUserId, "Food"), "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    ownerUserId,
                    ownerCategoryId,
                    "Someone else's proposal",
                    null,
                    100,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(30));
            long callerUserId = storedUserId("pending-messages-caller-user");

            Set<IncomingMessageId> answer = adapter.findWithPendingProposals(callerUserId, List.of(reference));

            assertThat(answer).isEmpty();
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
                "when create() hits a non-constraint failure - then throws PersistenceFailedException, not EntityNotFoundException")
        void whenCreateHitsNonConstraintDatabaseFailure_thenThrowsPersistenceFailedExceptionNotEntityNotFound() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseProposalEntityRepository.save(any())).thenThrow(frameworkException);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    1L,
                    1L,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    Instant.now());

            assertThatThrownBy(() -> mockedAdapter.create(proposal))
                    .isInstanceOf(PersistenceFailedException.class)
                    .isNotInstanceOf(EntityNotFoundException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when create() hits a constraint violation naming neither of its foreign keys - then throws PersistenceFailedException")
        void whenCreateHitsConstraintViolationNamingNeitherForeignKey_thenThrowsPersistenceFailedException() {
            SQLException sqlException = new SQLException(
                    "ERROR: insert or update on table \"expense_proposal\" violates foreign key constraint \"some_other_table_fkey\"",
                    "23503");
            DataIntegrityViolationException frameworkException =
                    new DataIntegrityViolationException("constraint violation", sqlException);
            when(mockedExpenseProposalEntityRepository.save(any())).thenThrow(frameworkException);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    1L,
                    1L,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    Instant.now());

            assertThatThrownBy(() -> mockedAdapter.create(proposal))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when findSummariesByMessageReference() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenFindSummariesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseProposalEntityRepository.findSummariesByMessageReference(any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.findSummariesByMessageReference(
                            1L, IncomingMessageId.of(UUID.randomUUID().toString())))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName("when accept() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenAcceptHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseProposalEntityRepository.accept(any(), any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.accept(
                            1L, IncomingMessageId.of(UUID.randomUUID().toString()), Instant.now()))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName("when discard() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenDiscardHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseProposalEntityRepository.discard(any(), any())).thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.discard(
                            1L, IncomingMessageId.of(UUID.randomUUID().toString())))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when called with an empty collection of message ids - then the answer is empty and no statement runs")
        void whenCalledWithEmptyCollectionOfMessageIds_thenAnswerIsEmptyAndNoStatementRuns() {
            Set<IncomingMessageId> answer = mockedAdapter.findWithPendingProposals(1L, List.of());

            assertThat(answer).isEmpty();
            verifyNoInteractions(mockedExpenseProposalEntityRepository);
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private long storedGroupingId(long userId, String name) {
        return CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, name);
    }

    private long storedCategoryId(long userId, long parentId, String name) {
        return CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, parentId, name);
    }

    private List<ExpenseProposalEntity> expenseProposalRowsFor(long userId) {
        return ExpenseProposalRowUtils.expenseProposalRowsFor(jdbcAggregateTemplate, userId);
    }

    private List<ExpenseEntity> expenseRowsFor(long userId) {
        return ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId);
    }
}
