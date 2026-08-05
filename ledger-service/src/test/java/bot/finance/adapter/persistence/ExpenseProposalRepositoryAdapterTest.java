package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ProposalSummary;
import bot.finance.common.CategoryRowUtils;
import bot.finance.common.ExpenseProposalRowUtils;
import bot.finance.common.ExpenseRowUtils;
import bot.finance.common.PersistenceAdapterTest;
import bot.finance.common.UserRowUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
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
                    MessageReference.newReference(),
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
            long categoryId = storedGroupingId(userId, "Utilities");
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Electric bill",
                    Optional.empty(),
                    new Money(4200, CurrencyCode.of("USD")),
                    MessageReference.newReference(),
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
            long categoryId = storedGroupingId(userId, "Dining");
            Instant nanosecondInstant = Instant.parse("2026-01-15T10:30:00.123456789Z");
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Dinner",
                    Optional.empty(),
                    new Money(3000, CurrencyCode.of("USD")),
                    MessageReference.newReference(),
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
            long categoryId = storedGroupingId(userId, "Boundary");
            String description = "a".repeat(500);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    description,
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    MessageReference.newReference(),
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
            long categoryId = storedGroupingId(userId, "Boundary");
            String description = "a".repeat(501);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    description,
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    MessageReference.newReference(),
                    Instant.now());

            assertThatThrownBy(() -> adapter.create(proposal)).isInstanceOf(InvalidExpenseProposalException.class);

            assertThat(expenseProposalRowsFor(userId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when called with a merchant exactly 255 characters long - then the row is written and carries the whole merchant")
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
                    MessageReference.newReference(),
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
            long categoryId = storedGroupingId(userId, "Boundary");
            String merchant = "a".repeat(256);
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    userId,
                    categoryId,
                    "Purchase",
                    Optional.of(merchant),
                    new Money(100, CurrencyCode.of("USD")),
                    MessageReference.newReference(),
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
                    MessageReference.newReference(),
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
            long categoryId = storedGroupingId(storedUserId("category-owner-for-unknown-proposal-user"), "Category");
            ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(
                    unknownUserId,
                    categoryId,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    MessageReference.newReference(),
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
            long firstCategoryId = storedGroupingId(firstUserId, "First Category");
            long secondUserId = storedUserId("second-proposal-user");
            long secondCategoryId = storedGroupingId(secondUserId, "Second Category");

            ExpenseProposal firstProposal = ExpenseProposal.newExpenseProposal(
                    firstUserId,
                    firstCategoryId,
                    "First purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    MessageReference.newReference(),
                    Instant.now());
            ExpenseProposal secondProposal = ExpenseProposal.newExpenseProposal(
                    secondUserId,
                    secondCategoryId,
                    "Second purchase",
                    Optional.empty(),
                    new Money(200, CurrencyCode.of("USD")),
                    MessageReference.newReference(),
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
                "when called with a stored user, a stored category and a proposal carrying a message reference - then the written row's message_reference column equals that reference's UUID")
        void whenCalledWithMessageReference_thenRowMessageReferenceColumnEqualsGivenReferenceUuid() {
            long userId = storedUserId("message-reference-proposal-user");
            long categoryId = storedGroupingId(userId, "Category");
            MessageReference reference = MessageReference.newReference();
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
            assertThat(rows).singleElement().satisfies(row -> assertThat(row.messageReference())
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
            MessageReference reference = MessageReference.newReference();
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
                "when called with one of two references a user's proposals were stored under - then only that reference's proposals come back")
        void whenUserHasProposalsUnderTwoReferences_thenOnlyRequestedReferencesProposalsComeBack() {
            long userId = storedUserId("summary-two-references-user");
            long parentId = storedGroupingId(userId, "Food");
            long categoryId = storedCategoryId(userId, parentId, "Groceries");
            MessageReference firstReference = MessageReference.newReference();
            MessageReference secondReference = MessageReference.newReference();
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
            MessageReference sharedReference = MessageReference.newReference();
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

            List<ProposalSummary> summaries =
                    adapter.findSummariesByMessageReference(userId, MessageReference.newReference());

            assertThat(summaries).isEmpty();
        }

        @Test
        @DisplayName(
                "when a stored proposal's merchant column is null - then that summary's merchant is Optional.empty()")
        void whenStoredProposalsMerchantColumnIsNull_thenSummaryMerchantIsEmpty() {
            long userId = storedUserId("summary-no-merchant-user");
            long parentId = storedGroupingId(userId, "Food");
            long categoryId = storedCategoryId(userId, parentId, "Groceries");
            MessageReference reference = MessageReference.newReference();
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
            MessageReference reference = MessageReference.newReference();
            MessageReference otherReference = MessageReference.newReference();
            Instant createdAt = Instant.now().minusSeconds(60);
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
                        assertThat(row.messageReference()).isEqualTo(reference.value());
                        assertThat(row.createdAt()).isEqualTo(truncatedNow);
                        assertThat(row.updatedAt()).isEqualTo(truncatedNow);
                    })
                    .anySatisfy(row -> {
                        assertThat(row.categoryId()).isEqualTo(categoryId);
                        assertThat(row.description()).isEqualTo("No merchant");
                        assertThat(row.merchant()).isNull();
                        assertThat(row.amountMinorUnits()).isEqualTo(2500);
                        assertThat(row.currencyCode()).isEqualTo("EUR");
                        assertThat(row.messageReference()).isEqualTo(reference.value());
                        assertThat(row.createdAt()).isEqualTo(truncatedNow);
                        assertThat(row.updatedAt()).isEqualTo(truncatedNow);
                    });
            assertThat(expenseProposalRowsFor(userId)).singleElement().satisfies(row -> assertThat(row.description())
                    .isEqualTo("Other reference"));
        }

        @Test
        @DisplayName(
                "when called for a reference under which the stored proposal's merchant column is null - then the written expense row's merchant column is null")
        void whenProposalMerchantColumnIsNull_thenWrittenExpenseRowMerchantColumnIsNull() {
            long userId = storedUserId("accept-null-merchant-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Utilities");
            MessageReference reference = MessageReference.newReference();
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
        @DisplayName(
                "when called with a stored user and a reference nothing was written under - then returns 0 and no expense row is written (D5)")
        void whenReferenceHasNoStoredProposals_thenReturnsZeroAndWritesNoExpenseRow() {
            long userId = storedUserId("accept-no-proposals-user");

            int moved = adapter.accept(userId, MessageReference.newReference(), Instant.now());

            assertThat(moved).isEqualTo(0);
            assertThat(expenseRowsFor(userId)).isEmpty();
        }

        @Test
        @DisplayName(
                "when called for the first of two users each holding one proposal under the same reference value - then returns 1, the first user's proposal is gone and the second user's survives untouched (D7)")
        void whenTwoUsersShareReferenceValue_thenReturnsOneAndOnlyFirstUsersProposalMoves() {
            long firstUserId = storedUserId("accept-shared-reference-first-user");
            long firstCategoryId = storedCategoryId(firstUserId, storedGroupingId(firstUserId, "Food"), "Groceries");
            long secondUserId = storedUserId("accept-shared-reference-second-user");
            long secondCategoryId = storedCategoryId(secondUserId, storedGroupingId(secondUserId, "Food"), "Groceries");
            MessageReference sharedReference = MessageReference.newReference();
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
        @DisplayName(
                "when called with a now carrying nanosecond precision - then both written timestamps equal that instant truncated to microseconds")
        void whenNowCarriesNanosecondPrecision_thenWrittenTimestampsAreTruncatedToMicroseconds() {
            long userId = storedUserId("accept-nanosecond-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            MessageReference reference = MessageReference.newReference();
            ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Dinner",
                    null,
                    3000,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(30));
            Instant nanosecondInstant = Instant.parse("2026-01-15T10:30:00.123456789Z");

            adapter.accept(userId, reference, nanosecondInstant);

            Instant truncated = nanosecondInstant.truncatedTo(ChronoUnit.MICROS);
            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> {
                assertThat(row.createdAt()).isEqualTo(truncated);
                assertThat(row.updatedAt()).isEqualTo(truncated);
            });
        }
    }

    @Nested
    @DisplayName("discarding proposals under a message reference")
    class Discard {

        @Test
        @DisplayName(
                "when called for a reference under which two proposals are stored, alongside a third proposal stored under a different reference - then returns 2, only the third proposal row survives, and no expense row is written (D14)")
        void
                whenTwoProposalsStoredUnderReferenceAndAThirdUnderAnother_thenReturnsTwoDeletesThemAndLeavesThirdProposal() {
            long userId = storedUserId("discard-two-proposals-user");
            long categoryId = storedCategoryId(userId, storedGroupingId(userId, "Food"), "Groceries");
            MessageReference reference = MessageReference.newReference();
            MessageReference otherReference = MessageReference.newReference();
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

            int discarded = adapter.discard(userId, MessageReference.newReference());

            assertThat(discarded).isEqualTo(0);
        }

        @Test
        @DisplayName(
                "when called for the first of two users each holding one proposal under the same reference value - then returns 1 and the second user's row survives (D7)")
        void whenTwoUsersShareReferenceValue_thenReturnsOneAndSecondUsersRowSurvives() {
            long firstUserId = storedUserId("discard-shared-reference-first-user");
            long firstCategoryId = storedCategoryId(firstUserId, storedGroupingId(firstUserId, "Food"), "Groceries");
            long secondUserId = storedUserId("discard-shared-reference-second-user");
            long secondCategoryId = storedCategoryId(secondUserId, storedGroupingId(secondUserId, "Food"), "Groceries");
            MessageReference sharedReference = MessageReference.newReference();
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
                    1L,
                    1L,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    MessageReference.newReference(),
                    Instant.now());

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
                    1L,
                    1L,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    MessageReference.newReference(),
                    Instant.now());

            assertThatThrownBy(() -> mockedAdapter.create(proposal))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when findSummariesByMessageReference() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void
                whenFindSummariesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseProposalEntityRepository.findSummariesByMessageReference(any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.findSummariesByMessageReference(1L, MessageReference.newReference()))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when accept() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void whenAcceptHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseProposalEntityRepository.accept(any(), any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.accept(1L, MessageReference.newReference(), Instant.now()))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when discard() hits a database failure - then throws PersistenceFailedException carrying the framework exception as its cause")
        void whenDiscardHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseProposalEntityRepository.discard(any(), any())).thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.discard(1L, MessageReference.newReference()))
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
