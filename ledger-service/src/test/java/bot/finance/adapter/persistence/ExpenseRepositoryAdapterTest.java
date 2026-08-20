package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.application.dto.CurrencyTotal;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.application.port.OutboxMeters;
import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.OutboxRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseFilter;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.ProposalIds;
import bot.finance.domain.value.SpendingPeriod;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@PersistenceAdapterTest
@Import({
    ExpenseRepositoryAdapter.class,
    LedgerEventOutbox.class,
    OutboxWriter.class,
    SpendingEventRenderer.class,
    Slf4jLoggerFactory.class
})
@MockitoBean(types = OutboxMeters.class)
class ExpenseRepositoryAdapterTest {

    @Autowired
    private ExpenseRepositoryAdapter adapter;

    @Autowired
    private ExpenseEntityRepository expenseEntityRepository;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private LedgerEventOutbox ledgerEventOutbox;

    @BeforeEach
    void clearOutbox() {
        OutboxRowUtils.clearOutbox(jdbcTemplate);
    }

    @Nested
    @DisplayName("creating an expense")
    class Create {

        @Test
        @DisplayName(
                "when called with an expense carrying a merchant - then the row holds what was given and carries a generated id")
        void whenCalledWithMerchant_thenRowWrittenWithGivenFieldsAndReturnedExpenseCarriesGeneratedId() {
            long userId = storedUserId("merchant-expense-user");
            long categoryId = leafCategoryId(userId, "Groceries");
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
                assertThat(row.incomingMessageId()).isNull();
            });
        }

        @Test
        @DisplayName(
                "when the expense carries no merchant - then the stored row and the returned expense both carry none")
        void whenCalledWithNoMerchant_thenStoredRowAndReturnedExpenseBothCarryNoMerchant() {
            long userId = storedUserId("no-merchant-expense-user");
            long categoryId = leafCategoryId(userId, "Utilities");
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
                "when the expense is stamped with nanosecond precision - then its timestamps are truncated to microseconds")
        void whenInstantCarriesNanosecondPrecision_thenTimestampsAreTruncatedToMicroseconds() {
            long userId = storedUserId("nanosecond-expense-user");
            long categoryId = leafCategoryId(userId, "Dining");
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
        @DisplayName("when the description is exactly 500 characters long - then the row carries the whole description")
        void whenDescriptionIsExactly500Characters_thenRowIsWrittenAndCarriesWholeDescription() {
            long userId = storedUserId("boundary-description-user");
            long categoryId = leafCategoryId(userId, "Boundary");
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
                "when the description is 501 characters long - then throws InvalidExpenseException and writes nothing")
        void whenDescriptionIs501Characters_thenThrowsInvalidExpenseExceptionAndWritesNothing() {
            long userId = storedUserId("overlong-description-user");
            long categoryId = leafCategoryId(userId, "Boundary");
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
        @DisplayName("when the merchant is exactly 255 characters long - then the row carries the whole merchant")
        void whenMerchantIsExactly255Characters_thenRowIsWrittenAndCarriesWholeMerchant() {
            long userId = storedUserId("boundary-merchant-user");
            long categoryId = leafCategoryId(userId, "Boundary");
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
                "when the merchant is 256 characters long - then throws InvalidExpenseException and writes nothing")
        void whenMerchantIs256Characters_thenThrowsInvalidExpenseExceptionAndWritesNothing() {
            long userId = storedUserId("overlong-merchant-user");
            long categoryId = leafCategoryId(userId, "Boundary");
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
                "when the category id names no stored category - then throws EntityNotFoundException for the category")
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
        @DisplayName("when the user id names no stored user - then throws EntityNotFoundException for the user")
        void whenUserIdNamesNoStoredUser_thenThrowsEntityNotFoundExceptionForUser() {
            long unknownUserId = 999_999_999L;
            long categoryId = leafCategoryId(storedUserId("category-owner-for-unknown-user"), "Category");
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
        @DisplayName("when two users each have an expense created - then each owns exactly its own row")
        void whenCalledForTwoDifferentUsers_thenEachOwnsExactlyItsOwnRow() {
            long firstUserId = storedUserId("first-expense-user");
            long firstCategoryId = leafCategoryId(firstUserId, "First Category");
            long secondUserId = storedUserId("second-expense-user");
            long secondCategoryId = leafCategoryId(secondUserId, "Second Category");

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

        @Test
        @DisplayName(
                "when called with a PENDING entry carrying a message id - then the row is written with that status and its message id")
        void whenCalledWithPendingEntryCarryingMessageId_thenRowIsWrittenWithThatStatusAndMessageId() {
            long userId = storedUserId("pending-expense-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            Expense proposal = Expense.newProposal(
                    userId,
                    categoryId,
                    "Awaiting confirmation",
                    Optional.empty(),
                    new Money(1500, CurrencyCode.of("USD")),
                    reference,
                    Instant.now());

            Expense created = adapter.create(proposal);

            assertThat(created.id()).isPresent();
            List<ExpenseEntity> rows = expenseRowsFor(userId);
            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.status()).isEqualTo(ExpenseStatus.PENDING.name());
                assertThat(row.incomingMessageId()).isEqualTo(reference.value());
            });
        }

        @Test
        @DisplayName("when the category id names a grouping - then nothing is stored and no event is appended")
        void whenCategoryIdNamesAGrouping_thenNothingStoredAndNoEventAppended() {
            long userId = storedUserId("ri02-create-under-grouping-user");
            long groupingId = groupingIdNamed(userId, "Standalone");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            Expense proposal = Expense.newProposal(
                    userId,
                    groupingId,
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    reference,
                    Instant.now());

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() -> adapter.create(proposal))
                    .extracting(EntityNotFoundException::entityType)
                    .isEqualTo("category");

            assertThat(expenseRowsFor(userId)).isEmpty();
            verifyNoInteractions(ledgerEventOutbox);
        }
    }

    @Nested
    @DisplayName("finding proposal summaries by message reference")
    class FindSummariesByMessageReference {

        @Test
        @DisplayName(
                "when two PENDING entries and one RECORDED share a message - then only the two PENDING ones come back, oldest first")
        void whenTwoPendingEntriesAndOneRecordedShareMessage_thenOnlyPendingOnesComeBackOldestFirst() {
            long userId = storedUserId("find-summaries-user");
            long groupingId = groupingIdNamed(userId, "Food");
            long categoryId = storedCategoryId(userId, groupingId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            Instant base = Instant.now().minusSeconds(60);
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Second pending",
                    null,
                    200,
                    "USD",
                    reference.value(),
                    base.plusSeconds(10),
                    ExpenseStatus.PENDING);
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "First pending",
                    null,
                    100,
                    "USD",
                    reference.value(),
                    base,
                    ExpenseStatus.PENDING);
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Recorded",
                    null,
                    300,
                    "USD",
                    reference.value(),
                    base.plusSeconds(20),
                    ExpenseStatus.RECORDED);

            List<ProposalSummary> summaries = adapter.findSummariesByMessageReference(userId, reference);

            assertThat(summaries).hasSize(2);
            assertThat(summaries.get(0)).satisfies(summary -> {
                assertThat(summary.description()).isEqualTo("First pending");
                assertThat(summary.categoryName()).isEqualTo("Groceries");
                assertThat(summary.groupingName()).isEqualTo("Food");
            });
            assertThat(summaries.get(1).description()).isEqualTo("Second pending");
        }
    }

    @Nested
    @DisplayName("accepting pending entries under a message reference")
    class Accept {

        @Test
        @DisplayName(
                "when two PENDING entries share a message and a third does not - then two are RECORDED and the third is untouched")
        void whenTwoPendingEntriesShareMessageAndThirdDoesNot_thenTwoAreRecordedAndThirdUntouched() {
            long userId = storedUserId("accept-two-entries-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            IncomingMessageId otherReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            Instant createdAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
            ExpenseEntity first = ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "First",
                    null,
                    100,
                    "USD",
                    reference.value(),
                    createdAt,
                    ExpenseStatus.PENDING);
            ExpenseEntity second = ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Second",
                    null,
                    200,
                    "USD",
                    reference.value(),
                    createdAt,
                    ExpenseStatus.PENDING);
            ExpenseEntity third = ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Third",
                    null,
                    300,
                    "USD",
                    otherReference.value(),
                    createdAt,
                    ExpenseStatus.PENDING);

            int accepted = adapter.accept(userId, reference, Instant.now());

            assertThat(accepted).isEqualTo(2);
            assertThat(expenseRowsFor(userId, ExpenseStatus.RECORDED))
                    .extracting(ExpenseEntity::id)
                    .containsExactlyInAnyOrder(first.id(), second.id());
            assertThat(expenseRowsFor(userId, ExpenseStatus.PENDING))
                    .singleElement()
                    .satisfies(row -> assertThat(row.id()).isEqualTo(third.id()));
        }

        @Test
        @DisplayName(
                "when the entries under the message are already RECORDED - then zero is answered and nothing changes")
        void whenEntriesUnderMessageAlreadyRecorded_thenZeroAnsweredAndNothingChanges() {
            long userId = storedUserId("accept-already-recorded-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseEntity recorded =
                    storedExpense(userId, categoryId, "Already recorded", 100, "USD", reference.value());

            int accepted = adapter.accept(userId, reference, Instant.now());

            assertThat(accepted).isEqualTo(0);
            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> {
                assertThat(row.id()).isEqualTo(recorded.id());
                assertThat(row.status()).isEqualTo(ExpenseStatus.RECORDED.name());
            });
        }

        @Test
        @DisplayName("when two people share a message id value - then accepting for one leaves the other's row PENDING")
        void whenTwoPeopleShareMessageIdValue_thenAcceptingForOneLeavesOtherPending() {
            long firstUserId = storedUserId("accept-shared-message-first-user");
            long firstCategoryId = leafCategoryId(firstUserId, "Groceries");
            long secondUserId = storedUserId("accept-shared-message-second-user");
            long secondCategoryId = leafCategoryId(secondUserId, "Dining");
            IncomingMessageId sharedReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    firstUserId,
                    firstCategoryId,
                    "First person's entry",
                    null,
                    100,
                    "USD",
                    sharedReference.value(),
                    Instant.now().minusSeconds(30),
                    ExpenseStatus.PENDING);
            ExpenseEntity secondPersonEntry = ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    secondUserId,
                    secondCategoryId,
                    "Second person's entry",
                    null,
                    200,
                    "USD",
                    sharedReference.value(),
                    Instant.now().minusSeconds(30),
                    ExpenseStatus.PENDING);

            int accepted = adapter.accept(firstUserId, sharedReference, Instant.now());

            assertThat(accepted).isEqualTo(1);
            assertThat(expenseRowsFor(secondUserId, ExpenseStatus.PENDING))
                    .singleElement()
                    .satisfies(row -> assertThat(row.id()).isEqualTo(secondPersonEntry.id()));
        }

        @Test
        @DisplayName(
                "when the instant carries nanosecond precision - then the stored updated_at is truncated to microseconds")
        void whenInstantCarriesNanosecondPrecision_thenStoredUpdatedAtIsTruncatedToMicroseconds() {
            long userId = storedUserId("accept-nanosecond-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Dinner",
                    null,
                    3000,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(30),
                    ExpenseStatus.PENDING);
            Instant nanosecondInstant = Instant.parse("2026-01-15T10:30:00.123456789Z");

            adapter.accept(userId, reference, nanosecondInstant);

            Instant truncated = nanosecondInstant.truncatedTo(ChronoUnit.MICROS);
            assertThat(expenseRowsFor(userId, ExpenseStatus.RECORDED))
                    .singleElement()
                    .satisfies(row -> assertThat(row.updatedAt()).isEqualTo(truncated));
        }
    }

    @Nested
    @DisplayName("discarding pending entries under a message reference")
    class Discard {

        @Test
        @DisplayName(
                "when two PENDING and one RECORDED entry share a message - then two is answered and no PENDING remains")
        void whenTwoPendingAndOneRecordedShareMessage_thenTwoAnsweredAndNoPendingRemains() {
            long userId = storedUserId("discard-two-pending-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "First",
                    null,
                    100,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(60),
                    ExpenseStatus.PENDING);
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Second",
                    null,
                    200,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(60),
                    ExpenseStatus.PENDING);
            ExpenseEntity recorded = ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Recorded",
                    null,
                    300,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(60),
                    ExpenseStatus.RECORDED);

            int discarded = adapter.discard(userId, reference, Instant.now());

            assertThat(discarded).isEqualTo(2);
            assertThat(expenseRowsFor(userId, ExpenseStatus.PENDING)).isEmpty();
            assertThat(expenseRowsFor(userId, ExpenseStatus.RECORDED))
                    .singleElement()
                    .satisfies(row -> assertThat(row.id()).isEqualTo(recorded.id()));
        }
    }

    @Nested
    @DisplayName("accepting entries by id")
    class AcceptByIds {

        @Test
        @DisplayName(
                "when two PENDING entries share a reported message - then the message comes back twice and both are RECORDED")
        void whenTwoPendingEntriesShareReportedMessage_thenMessageComesBackTwiceAndBothRecorded() {
            long userId = storedUserId("accept-by-ids-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseEntity first = ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "First",
                    null,
                    100,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(60),
                    ExpenseStatus.PENDING);
            ExpenseEntity second = ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Second",
                    null,
                    200,
                    "USD",
                    reference.value(),
                    Instant.now().minusSeconds(60),
                    ExpenseStatus.PENDING);
            ExpenseEntity alreadyRecorded = storedExpense(userId, categoryId, "Already recorded", 300, "USD", null);

            List<IncomingMessageId> answer = adapter.acceptByIds(
                    userId, ProposalIds.of(List.of(first.id(), second.id(), alreadyRecorded.id())), Instant.now());

            assertThat(answer).containsExactlyInAnyOrder(reference, reference);
            assertThat(expenseRowsFor(userId, ExpenseStatus.RECORDED))
                    .extracting(ExpenseEntity::id)
                    .containsExactlyInAnyOrder(first.id(), second.id(), alreadyRecorded.id());
        }

        @Test
        @DisplayName(
                "when an id names another person's PENDING entry - then nothing is answered and their row stays PENDING")
        void whenIdNamesAnotherPersonsPendingEntry_thenNothingAnsweredAndTheirRowStaysPending() {
            long ownerUserId = storedUserId("accept-by-ids-owner-user");
            long ownerCategoryId = leafCategoryId(ownerUserId, "Groceries");
            ExpenseEntity ownerEntry = ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    ownerUserId,
                    ownerCategoryId,
                    "Owner's entry",
                    null,
                    100,
                    "USD",
                    IncomingMessageId.of(UUID.randomUUID().toString()).value(),
                    Instant.now().minusSeconds(30),
                    ExpenseStatus.PENDING);
            long callerUserId = storedUserId("accept-by-ids-caller-user");

            List<IncomingMessageId> answer =
                    adapter.acceptByIds(callerUserId, ProposalIds.of(List.of(ownerEntry.id())), Instant.now());

            assertThat(answer).isEmpty();
            assertThat(expenseRowsFor(ownerUserId, ExpenseStatus.PENDING))
                    .singleElement()
                    .satisfies(row -> assertThat(row.id()).isEqualTo(ownerEntry.id()));
        }
    }

    @Nested
    @DisplayName("finding which messages still hold a pending entry")
    class FindWithPendingProposals {

        @Test
        @DisplayName(
                "when one of two messages still holds PENDING and the other holds only RECORDED - then only the first is answered")
        void whenOneOfTwoMessagesStillHoldsPendingAndOtherOnlyRecorded_thenOnlyFirstAnswered() {
            long userId = storedUserId("find-with-pending-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            IncomingMessageId stillPending =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            IncomingMessageId nowRecorded =
                    IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Still pending",
                    null,
                    100,
                    "USD",
                    stillPending.value(),
                    Instant.now().minusSeconds(30),
                    ExpenseStatus.PENDING);
            storedExpense(userId, categoryId, "Now recorded", 200, "USD", nowRecorded.value());

            Set<IncomingMessageId> answer =
                    adapter.findWithPendingProposals(userId, List.of(stillPending, nowRecorded));

            assertThat(answer).containsExactly(stillPending);
        }
    }

    @Nested
    @DisplayName("counting expenses by message reference")
    class CountByMessageReference {

        @Test
        @DisplayName("when two expenses share a reference and one does not - then returns 2")
        void whenTwoExpensesStoredUnderReferenceAndOneUnderAnother_thenReturnsTwo() {
            long userId = storedUserId("count-two-expenses-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            IncomingMessageId otherReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
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

            int count = adapter.countByMessageReference(
                    userId, IncomingMessageId.of(UUID.randomUUID().toString()));

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("when every expense row carries a null message_reference - then returns 0")
        void whenAllExpensesHaveNullMessageReference_thenReturnsZero() {
            long userId = storedUserId("count-null-reference-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            storedExpense(userId, categoryId, "No message", 100, "USD", null);

            int count = adapter.countByMessageReference(
                    userId, IncomingMessageId.of(UUID.randomUUID().toString()));

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("when two users share a reference value - then counting for one ignores the other's expense")
        void whenTwoUsersShareReferenceValue_thenReturnsOne() {
            long firstUserId = storedUserId("count-shared-reference-first-user");
            long firstCategoryId = leafCategoryId(firstUserId, "Groceries");
            long secondUserId = storedUserId("count-shared-reference-second-user");
            long secondCategoryId = leafCategoryId(secondUserId, "Groceries");
            IncomingMessageId sharedReference =
                    IncomingMessageId.of(UUID.randomUUID().toString());
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
                "when four EUR and one HUF expense fall inside the period - then returns one total per currency, ordered by code")
        void whenFourEurExpensesAndOneHufExpenseInsidePeriod_thenReturnsOneTotalPerCurrencyOrderedByCode() {
            long userId = storedUserId("totals-mixed-currency-user");
            long categoryId = leafCategoryId(userId, "Groceries");
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
        @DisplayName("when expenses fall on the period's first and last day - then both are counted")
        void whenExpensesFallOnFirstAndLastDayBounds_thenBothAreCounted() {
            long userId = storedUserId("totals-inclusive-bounds-user");
            long categoryId = leafCategoryId(userId, "Groceries");
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
        @DisplayName("when expenses fall just outside the period's bounds - then neither is counted")
        void whenExpensesFallJustOutsidePeriodBounds_thenNeitherIsCounted() {
            long userId = storedUserId("totals-exclusive-bounds-user");
            long categoryId = leafCategoryId(userId, "Groceries");
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
                "when two users each have an expense inside the period - then only the requested user's is counted")
        void whenTwoUsersHaveExpensesInsidePeriod_thenOnlyRequestedUsersExpenseCounted() {
            long firstUserId = storedUserId("totals-two-users-first-user");
            long firstCategoryId = leafCategoryId(firstUserId, "Groceries");
            long secondUserId = storedUserId("totals-two-users-second-user");
            long secondCategoryId = leafCategoryId(secondUserId, "Groceries");
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
                "when a PENDING row exists inside the period and no RECORDED row does - then returns an empty list")
        void whenOnlyProposalRowExistsInsidePeriod_thenReturnsEmptyList() {
            long userId = storedUserId("totals-only-proposal-user");
            long parentId = groupingIdNamed(userId, "Food");
            long categoryId = CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, parentId, "Groceries");
            SpendingPeriod period = new SpendingPeriod(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));
            Instant insidePeriod =
                    period.from().atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Awaiting confirmation",
                    null,
                    500,
                    "USD",
                    IncomingMessageId.of(UUID.randomUUID().toString()).value(),
                    insidePeriod,
                    ExpenseStatus.PENDING);

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
                "when the filter is unnarrowed - then both kinds come back in one list, newest first, each with its status")
        void whenCalledWithUnnarrowedFilter_thenBothKindsComeBackNewestFirstEachWithItsStatus() {
            long userId = storedUserId("find-page-both-kinds-user");
            long categoryId = leafCategoryId(userId, "Groceries");
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
            long categoryId = leafCategoryId(userId, "Dining");
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
                "when the filter carries a from and a to - then only the rows inside come back, the last day included")
        void whenCalledWithDateRange_thenOnlyRowsInsideComeBackWithLastDayIncluded() {
            long userId = storedUserId("find-page-date-range-user");
            long categoryId = leafCategoryId(userId, "Travel");
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
                "when a second page is asked for at an offset of one page - then it continues the first, repeating no row")
        void whenSecondPageAskedForAtOffsetOfOnePage_thenItContinuesTheFirstRepeatingNoRow() {
            long userId = storedUserId("find-page-pagination-user");
            long categoryId = leafCategoryId(userId, "Shopping");
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
                "when the offset is beyond the stored rows - then an empty list comes back, not the last page again")
        void whenCalledWithOffsetBeyondStoredRows_thenEmptyListComesBackRatherThanLastPageAgain() {
            long userId = storedUserId("find-page-offset-overflow-user");
            long categoryId = leafCategoryId(userId, "Utilities");
            storedExpenseAt(userId, categoryId, "Only expense", 100, "USD", Instant.now());

            List<ExpenseEntry> withinRange = adapter.findPage(userId, unnarrowedFilter());
            List<ExpenseEntry> beyondRange =
                    adapter.findPage(userId, new ExpenseFilter(null, null, null, ExpenseFilter.DEFAULT_LIMIT, 10));

            assertThat(withinRange).hasSize(1);
            assertThat(beyondRange).isEmpty();
        }

        @Test
        @DisplayName("when the category id belongs to another user - then an empty list comes back")
        void whenCalledWithCategoryIdBelongingToAnotherUser_thenEmptyListComesBack() {
            long firstUserId = storedUserId("find-page-cross-user-first-user");
            long secondUserId = storedUserId("find-page-cross-user-second-user");
            long secondUsersCategoryId = leafCategoryId(secondUserId, "Second User Category");
            storedExpenseAt(secondUserId, secondUsersCategoryId, "Second user's expense", 100, "USD", Instant.now());
            storedExpenseAt(
                    firstUserId,
                    leafCategoryId(firstUserId, "First User Category"),
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
                "when two rows share a created_at, one under each status - then the order between them is the same on every call")
        void whenTwoRowsShareCreatedAtOneUnderEachStatus_thenOrderIsTheSameOnEveryCall() {
            long userId = storedUserId("find-page-tie-break-user");
            long categoryId = leafCategoryId(userId, "Entertainment");
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
        @DisplayName("when the filter is unnarrowed - then the answer is every row the user has, across both statuses")
        void whenCalledWithUnnarrowedFilter_thenAnswerIsEveryRowAcrossBothStatuses() {
            long userId = storedUserId("count-matching-unnarrowed-user");
            long categoryId = leafCategoryId(userId, "Groceries");
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
            long categoryId = leafCategoryId(userId, "Dining");
            storedExpenseAt(userId, categoryId, "Recorded one", 100, "USD", Instant.now());
            storedExpenseAt(userId, categoryId, "Recorded two", 200, "USD", Instant.now());
            storedProposalAt(userId, categoryId, "Pending one", 300, "USD", Instant.now());

            long count = adapter.countMatching(
                    userId, new ExpenseFilter(ExpenseStatus.PENDING, null, null, ExpenseFilter.DEFAULT_LIMIT, 0));

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("when the filter carries a limit and an offset - then the answer ignores both")
        void whenFilterCarriesLimitAndOffset_thenAnswerIgnoresThem() {
            long userId = storedUserId("count-matching-ignores-paging-user");
            long categoryId = leafCategoryId(userId, "Shopping");
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
            long secondUsersCategoryId = leafCategoryId(secondUserId, "Second User Category");
            storedExpenseAt(secondUserId, secondUsersCategoryId, "Second user's expense", 100, "USD", Instant.now());

            long forSecondUsersOwnCategory = adapter.countMatching(
                    secondUserId, new ExpenseFilter(null, secondUsersCategoryId, null, ExpenseFilter.DEFAULT_LIMIT, 0));
            long forFirstUserWithSecondUsersCategory = adapter.countMatching(
                    firstUserId, new ExpenseFilter(null, secondUsersCategoryId, null, ExpenseFilter.DEFAULT_LIMIT, 0));

            assertThat(forSecondUsersOwnCategory).isEqualTo(1);
            assertThat(forFirstUserWithSecondUsersCategory).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("refiling a recorded expense under another category")
    class Refile {

        @Test
        @DisplayName(
                "when called with another of the caller's categories - then the entry and stored row both carry the new category")
        void whenCalledWithAnotherCategory_thenAnswersEntryWithNewCategoryAndStoredRowCarriesIt() {
            long userId = storedUserId("refile-new-category-user");
            long groupingId = groupingIdNamed(userId, "Groceries");
            long originalCategoryId = storedCategoryId(userId, groupingId, "Supermarkets");
            long newCategoryId = storedCategoryId(userId, groupingId, "Dining");
            ExpenseEntity stored = ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    originalCategoryId,
                    "Weekly shop",
                    "Trader Joe's",
                    1500,
                    "USD",
                    null,
                    Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MICROS),
                    ExpenseStatus.RECORDED);

            Optional<ExpenseEntry> refiled =
                    adapter.refile(userId, stored.id(), newCategoryId, ExpenseStatus.RECORDED, Instant.now());

            assertThat(refiled).isPresent();
            assertThat(refiled.get().categoryId()).isEqualTo(newCategoryId);
            assertThat(refiled.get().description()).isEqualTo("Weekly shop");
            assertThat(refiled.get().merchant()).contains("Trader Joe's");
            assertThat(refiled.get().money()).isEqualTo(new Money(1500, CurrencyCode.of("USD")));
            assertThat(refiled.get().createdAt()).isEqualTo(stored.createdAt());
            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> assertThat(row.categoryId())
                    .isEqualTo(newCategoryId));
        }

        @Test
        @DisplayName(
                "when refiling an expense created earlier - then created_at is untouched and updated_at carries the given instant")
        void whenCalledForExpenseCreatedEarlierDay_thenCreatedAtUntouchedAndUpdatedAtCarriesInstantGiven() {
            long userId = storedUserId("refile-earlier-day-user");
            long groupingId = groupingIdNamed(userId, "Groceries");
            long originalCategoryId = storedCategoryId(userId, groupingId, "Supermarkets");
            long newCategoryId = storedCategoryId(userId, groupingId, "Dining");
            Instant createdAt = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
            ExpenseEntity stored = storedExpenseAt(userId, originalCategoryId, "Old purchase", 1000, "USD", createdAt);
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

            Optional<ExpenseEntry> refiled =
                    adapter.refile(userId, stored.id(), newCategoryId, ExpenseStatus.RECORDED, now);

            assertThat(refiled).isPresent();
            assertThat(refiled.get().createdAt()).isEqualTo(createdAt);
            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> {
                assertThat(row.createdAt()).isEqualTo(createdAt);
                assertThat(row.updatedAt()).isEqualTo(now);
            });
        }

        @Test
        @DisplayName(
                "when called with the category already filed under - then the answer carries the row and only updated_at moved")
        void whenCalledWithSameCategory_thenAnswerCarriesRowAndOnlyUpdatedAtMoved() {
            long userId = storedUserId("refile-same-category-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            ExpenseEntity stored = storedExpenseAt(userId, categoryId, "Weekly shop", 1500, "USD", createdAt);
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

            Optional<ExpenseEntry> refiled =
                    adapter.refile(userId, stored.id(), categoryId, ExpenseStatus.RECORDED, now);

            assertThat(refiled).isPresent();
            assertThat(refiled.get().categoryId()).isEqualTo(categoryId);
            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> {
                assertThat(row.categoryId()).isEqualTo(categoryId);
                assertThat(row.createdAt()).isEqualTo(createdAt);
                assertThat(row.updatedAt()).isEqualTo(now);
            });
        }

        @Test
        @DisplayName(
                "when called for another person's expense - then the answer is empty and their row keeps its original category")
        void whenCalledForAnotherPersonsExpense_thenAnswerIsEmptyAndTheirRowKeepsOriginalCategory() {
            long ownerUserId = storedUserId("refile-cross-user-owner");
            long ownerCategoryId = leafCategoryId(ownerUserId, "Groceries");
            ExpenseEntity ownerExpense =
                    storedExpense(ownerUserId, ownerCategoryId, "Owner's purchase", 100, "USD", null);
            long callerUserId = storedUserId("refile-cross-user-caller");
            long callerCategoryId = leafCategoryId(callerUserId, "Dining");

            Optional<ExpenseEntry> refiled = adapter.refile(
                    callerUserId, ownerExpense.id(), callerCategoryId, ExpenseStatus.RECORDED, Instant.now());

            assertThat(refiled).isEmpty();
            assertThat(expenseRowsFor(ownerUserId)).singleElement().satisfies(row -> assertThat(row.categoryId())
                    .isEqualTo(ownerCategoryId));
        }

        @Test
        @DisplayName(
                "when the id names a pending proposal, not an expense - then the answer is empty and the proposal row is untouched")
        void whenIdNamesCallersPendingProposal_thenAnswerIsEmptyAndProposalRowUntouched() {
            long userId = storedUserId("refile-names-proposal-user");
            long groupingId = groupingIdNamed(userId, "Groceries");
            long originalCategoryId = storedCategoryId(userId, groupingId, "Supermarkets");
            long newCategoryId = storedCategoryId(userId, groupingId, "Dining");
            ExpenseEntity proposal =
                    storedProposalAt(userId, originalCategoryId, "Pending purchase", 500, "USD", Instant.now());

            Optional<ExpenseEntry> refiled =
                    adapter.refile(userId, proposal.id(), newCategoryId, ExpenseStatus.RECORDED, Instant.now());

            assertThat(refiled).isEmpty();
            assertThat(expenseRowsFor(userId, ExpenseStatus.PENDING))
                    .singleElement()
                    .satisfies(row -> assertThat(row.categoryId()).isEqualTo(originalCategoryId));
        }

        @Test
        @DisplayName(
                "when the entry's merchant is absent - then the answered entry carries no merchant and nothing throws")
        void whenMerchantAbsent_thenAnsweredEntryCarriesNoMerchantAndNothingThrows() {
            long userId = storedUserId("refile-no-merchant-user");
            long groupingId = groupingIdNamed(userId, "Groceries");
            long originalCategoryId = storedCategoryId(userId, groupingId, "Supermarkets");
            long newCategoryId = storedCategoryId(userId, groupingId, "Dining");
            ExpenseEntity stored = storedExpense(userId, originalCategoryId, "No merchant purchase", 100, "USD", null);

            Optional<ExpenseEntry> refiled =
                    adapter.refile(userId, stored.id(), newCategoryId, ExpenseStatus.RECORDED, Instant.now());

            assertThat(refiled).isPresent();
            assertThat(refiled.get().merchant()).isEmpty();
        }

        @Test
        @DisplayName(
                "when the instant carries sub-microsecond precision - then the stored updated_at is truncated, not rounded")
        void whenInstantCarriesSubMicrosecondPrecision_thenStoredUpdatedAtIsTruncatedNotRounded() {
            long userId = storedUserId("refile-sub-microsecond-user");
            long groupingId = groupingIdNamed(userId, "Groceries");
            long originalCategoryId = storedCategoryId(userId, groupingId, "Supermarkets");
            long newCategoryId = storedCategoryId(userId, groupingId, "Dining");
            ExpenseEntity stored = storedExpense(userId, originalCategoryId, "Purchase", 100, "USD", null);
            Instant nanosecondInstant = Instant.parse("2026-01-15T10:30:00.123456789Z");

            adapter.refile(userId, stored.id(), newCategoryId, ExpenseStatus.RECORDED, nanosecondInstant);

            Instant truncated = nanosecondInstant.truncatedTo(ChronoUnit.MICROS);
            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> assertThat(row.updatedAt())
                    .isEqualTo(truncated));
        }

        @Test
        @DisplayName("when the target category id names a grouping - then the entry keeps its category and no event "
                + "is appended")
        void whenTargetCategoryIdNamesAGrouping_thenEntryKeepsItsCategoryAndNoEventAppended() {
            long userId = storedUserId("ri02-refile-onto-grouping-user");
            long groupingId = groupingIdNamed(userId, "Groceries");
            long originalCategoryId = storedCategoryId(userId, groupingId, "Supermarkets");
            ExpenseEntity stored = storedExpense(userId, originalCategoryId, "Purchase", 100, "USD", null);

            assertThatExceptionOfType(EntityNotFoundException.class)
                    .isThrownBy(() ->
                            adapter.refile(userId, stored.id(), groupingId, ExpenseStatus.RECORDED, Instant.now()))
                    .extracting(EntityNotFoundException::entityType)
                    .isEqualTo("category");

            assertThat(expenseRowsFor(userId)).singleElement().satisfies(row -> assertThat(row.categoryId())
                    .isEqualTo(originalCategoryId));
            verifyNoInteractions(ledgerEventOutbox);
        }

        @Test
        @DisplayName(
                "when an id names no entry of the caller's under that status - then the answer is empty and nothing is inserted")
        void whenIdNamesNoEntryOfCallersUnderThatStatus_thenAnswerIsEmptyAndNothingInserted() {
            long userId = storedUserId("ri02-refile-no-matching-entry-user");
            long groupingId = groupingIdNamed(userId, "Groceries");
            long newCategoryId = storedCategoryId(userId, groupingId, "Dining");
            long unknownEntryId = 999_999_999L;

            Optional<ExpenseEntry> refiled =
                    adapter.refile(userId, unknownEntryId, newCategoryId, ExpenseStatus.RECORDED, Instant.now());

            assertThat(refiled).isEmpty();
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
        private final CategoryEntityRepository mockedCategoryEntityRepository = mock(CategoryEntityRepository.class);
        private final LedgerEventOutbox mockedLedgerEventOutbox = mock(LedgerEventOutbox.class);
        private final ExpenseRepositoryAdapter mockedAdapter = new ExpenseRepositoryAdapter(
                mockedExpenseEntityRepository, mockedCategoryEntityRepository, mockedLedgerEventOutbox);

        @Test
        @DisplayName(
                "when create() hits a non-constraint failure - then throws PersistenceFailedException, not EntityNotFoundException")
        void whenCreateHitsNonConstraintDatabaseFailure_thenThrowsPersistenceFailedExceptionNotEntityNotFound() {
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
                "when create() hits a constraint violation naming neither of its foreign keys - then throws PersistenceFailedException")
        void whenCreateHitsConstraintViolationNamingNeitherForeignKey_thenThrowsPersistenceFailedException() {
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
                "when findSummariesByMessageReference() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenFindSummariesByMessageReferenceHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseEntityRepository.findSummariesByMessageReference(any(), any()))
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
            when(mockedExpenseEntityRepository.accept(any(), any(), any())).thenThrow(frameworkException);

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
            when(mockedExpenseEntityRepository.discard(any(), any())).thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.discard(
                            1L, IncomingMessageId.of(UUID.randomUUID().toString()), Instant.now()))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName("when acceptByIds() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenAcceptByIdsHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseEntityRepository.acceptByIds(any(), any(), any())).thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.acceptByIds(1L, ProposalIds.of(List.of(1L)), Instant.now()))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when findWithPendingProposals() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenFindWithPendingProposalsHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseEntityRepository.findWithPendingProposals(any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.findWithPendingProposals(
                            1L, List.of(IncomingMessageId.of(UUID.randomUUID().toString()))))
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
            verifyNoInteractions(mockedExpenseEntityRepository);
        }

        @Test
        @DisplayName(
                "when countByMessageReference() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenCountByMessageReferenceHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseEntityRepository.countByMessageReference(any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.countByMessageReference(
                            1L, IncomingMessageId.of(UUID.randomUUID().toString())))
                    .isInstanceOf(PersistenceFailedException.class)
                    .isNotInstanceOf(EntityNotFoundException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when totalsByCurrency() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenTotalsByCurrencyHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
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
        @DisplayName("when findPage() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenFindPageHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            ExpenseEntityRepository throwingRepository = mock(ExpenseEntityRepository.class, invocation -> {
                throw frameworkException;
            });
            ExpenseRepositoryAdapter throwingAdapter = new ExpenseRepositoryAdapter(
                    throwingRepository, mock(CategoryEntityRepository.class), mock(LedgerEventOutbox.class));
            ExpenseFilter filter = new ExpenseFilter(null, null, null, ExpenseFilter.DEFAULT_LIMIT, 0);

            assertThatThrownBy(() -> throwingAdapter.findPage(1L, filter))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName(
                "when countMatching() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenCountMatchingHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            ExpenseEntityRepository throwingRepository = mock(ExpenseEntityRepository.class, invocation -> {
                throw frameworkException;
            });
            ExpenseRepositoryAdapter throwingAdapter = new ExpenseRepositoryAdapter(
                    throwingRepository, mock(CategoryEntityRepository.class), mock(LedgerEventOutbox.class));
            ExpenseFilter filter = new ExpenseFilter(null, null, null, ExpenseFilter.DEFAULT_LIMIT, 0);

            assertThatThrownBy(() -> throwingAdapter.countMatching(1L, filter))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }

        @Test
        @DisplayName("when refile() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenRefileHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            QueryTimeoutException frameworkException = new QueryTimeoutException("statement timed out");
            when(mockedExpenseEntityRepository.refile(any(), any(), any(), any(), any()))
                    .thenThrow(frameworkException);

            assertThatThrownBy(() -> mockedAdapter.refile(1L, 1L, 1L, ExpenseStatus.RECORDED, Instant.now()))
                    .isInstanceOf(PersistenceFailedException.class)
                    .extracting(Throwable::getCause)
                    .isEqualTo(frameworkException);
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private long groupingIdNamed(long userId, String name) {
        return CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, name);
    }

    /**
     * Spending is filed under a category, never under a grouping, so a test needing somewhere to file an expense
     * takes a leaf under a grouping of its own.
     */
    private long leafCategoryId(long userId, String name) {
        return storedCategoryId(userId, groupingIdNamed(userId, name + " grouping"), name);
    }

    private long storedCategoryId(long userId, long parentId, String name) {
        return CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, parentId, name);
    }

    private List<ExpenseEntity> expenseRowsFor(long userId) {
        return ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId);
    }

    private List<ExpenseEntity> expenseRowsFor(long userId, ExpenseStatus status) {
        return ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId, status);
    }

    // countByMessageReference's rows have to carry a message_reference, and totalsByCurrency's an
    // exact createdAt to probe the period's bounds - neither of which adapter.create() writes. So
    // both are seeded directly, the way ExpenseRowUtils.storedExpense seeds a row of a given status.
    private ExpenseEntity storedExpense(
            long userId,
            long categoryId,
            String description,
            long amountMinorUnits,
            String currencyCode,
            String incomingMessageId) {
        return ExpenseRowUtils.storedExpense(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                description,
                null,
                amountMinorUnits,
                currencyCode,
                incomingMessageId,
                Instant.now(),
                ExpenseStatus.RECORDED);
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
                createdAt,
                ExpenseStatus.RECORDED);
    }

    private ExpenseEntity storedProposalAt(
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
                IncomingMessageId.of(UUID.randomUUID().toString()).value(),
                createdAt,
                ExpenseStatus.PENDING);
    }

    private ExpenseFilter unnarrowedFilter() {
        return new ExpenseFilter(null, null, null, ExpenseFilter.DEFAULT_LIMIT, 0);
    }
}
