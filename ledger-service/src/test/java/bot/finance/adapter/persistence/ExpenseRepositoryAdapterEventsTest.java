package bot.finance.adapter.persistence;

import static bot.finance.common.fixtures.JsonUtils.readJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.OutboxRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.ProposalIds;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Which fact each write publishes, one nested class per writing method. Publishing is one concern spread across
 * every write, so the catalogue is read here rather than assembled from the method groups in
 * {@link ExpenseRepositoryAdapterTest}, which owns everything else those methods do.
 *
 * <p>The outbox row is deleted in the same transaction that wrote it, so nothing can be read back off the table.
 * The outbox is a spy, and what was handed to {@code insert} is the only record of what a write produced.
 */
@PersistenceAdapterTest
@Import({ExpenseRepositoryAdapter.class, LedgerEventOutbox.class, SpendingEventRenderer.class})
class ExpenseRepositoryAdapterEventsTest {

    @Autowired
    private ExpenseRepositoryAdapter adapter;

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
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("when a pending entry under a grouped category is created - then a ProposalCreated fact carries "
                + "the entry")
        void whenPendingEntryUnderGroupedCategoryCreated_thenProposalCreatedFactCarriesTheEntry() {
            long userId = storedUserId("events-create-pending-user");
            long groupingId = groupingIdNamed(userId, "Food");
            long categoryId = storedCategoryId(userId, groupingId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            Expense proposal = Expense.newProposal(
                    userId,
                    categoryId,
                    "Weekly shop",
                    Optional.empty(),
                    new Money(1500, CurrencyCode.of("USD")),
                    reference,
                    Instant.now());

            Expense created = adapter.create(proposal);

            assertThat(insertedEvents()).singleElement().satisfies(event -> {
                assertThat(event.type()).isEqualTo(LedgerEventType.ProposalCreated);
                JsonNode payload = readJson(event.payload());
                assertThat(payload.get("expenseId").asLong())
                        .isEqualTo(created.id().orElseThrow());
                assertThat(payload.get("incomingMessageId").asText()).isEqualTo(reference.value());
                assertThat(payload.get("status").asText()).isEqualTo(ExpenseStatus.PENDING.name());
                assertThat(payload.get("category").get("name").asText()).isEqualTo("Groceries");
                assertThat(payload.get("grouping").get("name").asText()).isEqualTo("Food");
            });
        }

        @Test
        @DisplayName("when a recorded entry has no message id - then an ExpenseRecorded fact carries a null message id")
        void whenRecordedEntryHasNoMessageId_thenExpenseRecordedFactCarriesNullMessageId() {
            long userId = storedUserId("events-create-recorded-user");
            Expense expense = Expense.newExpense(
                    userId,
                    leafCategoryId(userId, "Utilities"),
                    "Electric bill",
                    Optional.empty(),
                    new Money(4200, CurrencyCode.of("USD")),
                    Instant.now());

            adapter.create(expense);

            assertThat(insertedEvents()).singleElement().satisfies(event -> {
                assertThat(event.type()).isEqualTo(LedgerEventType.ExpenseRecorded);
                JsonNode payload = readJson(event.payload());
                assertThat(payload.get("incomingMessageId").isNull()).isTrue();
                assertThat(payload.get("status").asText()).isEqualTo(ExpenseStatus.RECORDED.name());
            });
        }

        @Test
        @DisplayName("when a write commits - then the outbox it wrote through is empty again")
        void whenWriteCommits_thenOutboxIsEmptyAgain() {
            long userId = storedUserId("events-create-outbox-empty-user");
            Expense expense = Expense.newExpense(
                    userId,
                    leafCategoryId(userId, "Groceries"),
                    "Weekly shop",
                    Optional.empty(),
                    new Money(1500, CurrencyCode.of("USD")),
                    Instant.now());

            adapter.create(expense);

            assertThat(OutboxRowUtils.outboxRowCount(jdbcTemplate)).isZero();
        }

        @Test
        @DisplayName("when the outbox's insert fails - then the write fails and its rows are never deleted")
        void whenOutboxInsertFails_thenWriteFailsAndRowsNeverDeleted() {
            doThrow(new RuntimeException("outbox insert failed"))
                    .when(ledgerEventOutbox)
                    .insert(any());
            long userId = storedUserId("events-create-outbox-failure-user");
            Expense proposal = Expense.newProposal(
                    userId,
                    leafCategoryId(userId, "Groceries"),
                    "Purchase",
                    Optional.empty(),
                    new Money(100, CurrencyCode.of("USD")),
                    IncomingMessageId.of(UUID.randomUUID().toString()),
                    Instant.now());

            assertThatThrownBy(() -> adapter.create(proposal)).isInstanceOf(PersistenceFailedException.class);

            verify(ledgerEventOutbox, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("accept()")
    class Accept {

        @Test
        @DisplayName("when three pending entries are accepted - then one ProposalAccepted fact names each of them")
        void whenThreePendingEntriesAccepted_thenOneProposalAcceptedFactNamesEachOfThem() {
            long userId = storedUserId("events-accept-three-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseEntity first = storedPending(userId, categoryId, "First", 100, reference);
            ExpenseEntity second = storedPending(userId, categoryId, "Second", 200, reference);
            ExpenseEntity third = storedPending(userId, categoryId, "Third", 300, reference);

            adapter.accept(userId, reference, Instant.now());

            List<LedgerEvent> events = insertedEvents();
            assertThat(events).hasSize(3).allSatisfy(event -> {
                assertThat(event.type()).isEqualTo(LedgerEventType.ProposalAccepted);
                assertThat(readJson(event.payload()).get("status").asText()).isEqualTo(ExpenseStatus.RECORDED.name());
            });
            assertThat(expenseIdsOf(events)).containsExactlyInAnyOrder(first.id(), second.id(), third.id());
        }

        @Test
        @DisplayName("when the entries under the message are already recorded - then nothing is published")
        void whenEntriesUnderMessageAlreadyRecorded_thenNothingIsPublished() {
            long userId = storedUserId("events-accept-already-recorded-user");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseRowUtils.storedExpense(
                    jdbcAggregateTemplate,
                    userId,
                    leafCategoryId(userId, "Groceries"),
                    "Already recorded",
                    null,
                    100,
                    "USD",
                    reference.value(),
                    Instant.now(),
                    ExpenseStatus.RECORDED);

            adapter.accept(userId, reference, Instant.now());

            verify(ledgerEventOutbox, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("discard()")
    class Discard {

        @Test
        @DisplayName("when two pending entries are discarded - then a ProposalDiscarded fact carries each as it was")
        void whenTwoPendingEntriesDiscarded_thenProposalDiscardedFactCarriesEachAsItWas() {
            long userId = storedUserId("events-discard-two-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            IncomingMessageId reference = IncomingMessageId.of(UUID.randomUUID().toString());
            ExpenseEntity first = storedPending(userId, categoryId, "First", 100, reference);
            ExpenseEntity second = storedPending(userId, categoryId, "Second", 200, reference);
            Instant discardedAt = Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.MICROS);

            adapter.discard(userId, reference, discardedAt);

            List<LedgerEvent> events = insertedEvents();
            assertThat(events).hasSize(2).allSatisfy(event -> {
                assertThat(event.type()).isEqualTo(LedgerEventType.ProposalDiscarded);
                assertThat(event.occurredAt()).isEqualTo(discardedAt);
                assertThat(readJson(event.payload()).get("status").asText()).isEqualTo(ExpenseStatus.PENDING.name());
            });
            assertThat(expenseIdsOf(events)).containsExactlyInAnyOrder(first.id(), second.id());
        }

        @Test
        @DisplayName("when a message holds nothing pending - then nothing is published")
        void whenMessageHoldsNothingPending_thenNothingIsPublished() {
            long userId = storedUserId("events-discard-nothing-pending-user");

            adapter.discard(userId, IncomingMessageId.of(UUID.randomUUID().toString()), Instant.now());

            verify(ledgerEventOutbox, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("acceptByIds()")
    class AcceptByIds {

        @Test
        @DisplayName("when two entries on different messages are accepted by id - then each gets a ProposalAccepted "
                + "fact")
        void whenTwoEntriesOnDifferentMessagesAcceptedById_thenEachGetsProposalAcceptedFact() {
            long userId = storedUserId("events-accept-by-ids-user");
            long categoryId = leafCategoryId(userId, "Groceries");
            ExpenseEntity first = storedPending(
                    userId,
                    categoryId,
                    "First",
                    100,
                    IncomingMessageId.of(UUID.randomUUID().toString()));
            ExpenseEntity second = storedPending(
                    userId,
                    categoryId,
                    "Second",
                    200,
                    IncomingMessageId.of(UUID.randomUUID().toString()));

            adapter.acceptByIds(userId, ProposalIds.of(List.of(first.id(), second.id())), Instant.now());

            List<LedgerEvent> events = insertedEvents();
            assertThat(events).hasSize(2).allSatisfy(event -> assertThat(event.type())
                    .isEqualTo(LedgerEventType.ProposalAccepted));
            assertThat(expenseIdsOf(events)).containsExactlyInAnyOrder(first.id(), second.id());
        }

        @Test
        @DisplayName("when an id names another person's pending entry - then nothing is published")
        void whenIdNamesAnotherPersonsPendingEntry_thenNothingIsPublished() {
            long ownerUserId = storedUserId("events-accept-by-ids-owner-user");
            ExpenseEntity ownerEntry = storedPending(
                    ownerUserId,
                    leafCategoryId(ownerUserId, "Groceries"),
                    "Owner's entry",
                    100,
                    IncomingMessageId.of(UUID.randomUUID().toString()));
            long callerUserId = storedUserId("events-accept-by-ids-caller-user");

            adapter.acceptByIds(callerUserId, ProposalIds.of(List.of(ownerEntry.id())), Instant.now());

            verify(ledgerEventOutbox, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("refile()")
    class Refile {

        @Test
        @DisplayName("when a recorded entry is refiled to another grouping - then an ExpenseRefiled fact names where "
                + "it now sits")
        void whenRecordedEntryRefiledToAnotherGrouping_thenExpenseRefiledFactNamesWhereItNowSits() {
            long userId = storedUserId("events-refile-recorded-user");
            long originalCategoryId = storedCategoryId(userId, groupingIdNamed(userId, "Food"), "Supermarkets");
            long newCategoryId = storedCategoryId(userId, groupingIdNamed(userId, "Leisure"), "Dining");
            ExpenseEntity stored = storedRecorded(userId, originalCategoryId, "Weekly shop", 1500);

            adapter.refile(userId, stored.id(), newCategoryId, ExpenseStatus.RECORDED, Instant.now());

            assertThat(insertedEvents()).singleElement().satisfies(event -> {
                assertThat(event.type()).isEqualTo(LedgerEventType.ExpenseRefiled);
                JsonNode payload = readJson(event.payload());
                assertThat(payload.get("category").get("name").asText()).isEqualTo("Dining");
                assertThat(payload.get("grouping").get("name").asText()).isEqualTo("Leisure");
            });
        }

        @Test
        @DisplayName("when a pending entry is refiled - then the fact is a ProposalRefiled")
        void whenPendingEntryRefiled_thenFactIsProposalRefiled() {
            long userId = storedUserId("events-refile-pending-user");
            long groupingId = groupingIdNamed(userId, "Groceries");
            long originalCategoryId = storedCategoryId(userId, groupingId, "Supermarkets");
            long newCategoryId = storedCategoryId(userId, groupingId, "Dining");
            ExpenseEntity proposal = storedPending(
                    userId,
                    originalCategoryId,
                    "Pending purchase",
                    500,
                    IncomingMessageId.of(UUID.randomUUID().toString()));

            adapter.refile(userId, proposal.id(), newCategoryId, ExpenseStatus.PENDING, Instant.now());

            assertThat(insertedEvents()).singleElement().satisfies(event -> assertThat(event.type())
                    .isEqualTo(LedgerEventType.ProposalRefiled));
        }

        @Test
        @DisplayName("when the category was renamed by SQL after the entry was stored - then the fact carries the "
                + "new name")
        void whenCategoryRenamedAfterEntryStored_thenFactCarriesTheNewName() {
            long userId = storedUserId("events-refile-renamed-category-user");
            long groupingId = groupingIdNamed(userId, "Groceries");
            long originalCategoryId = storedCategoryId(userId, groupingId, "Supermarkets");
            long newCategoryId = storedCategoryId(userId, groupingId, "Dining");
            ExpenseEntity stored = storedRecorded(userId, originalCategoryId, "Purchase", 100);
            CategoryRowUtils.renameCategory(jdbcAggregateTemplate, userId, newCategoryId, "Fine Dining");

            adapter.refile(userId, stored.id(), newCategoryId, ExpenseStatus.RECORDED, Instant.now());

            assertThat(insertedEvents()).singleElement().satisfies(event -> assertThat(readJson(event.payload())
                            .get("category")
                            .get("name")
                            .asText())
                    .isEqualTo("Fine Dining"));
        }

        @Test
        @DisplayName("when the id names no entry of theirs - then nothing is published")
        void whenIdNamesNoEntryOfTheirs_thenNothingIsPublished() {
            long userId = storedUserId("events-refile-unknown-entry-user");
            long categoryId = leafCategoryId(userId, "Groceries");

            adapter.refile(userId, 999_999_999L, categoryId, ExpenseStatus.RECORDED, Instant.now());

            verify(ledgerEventOutbox, never()).insert(any());
        }
    }

    private List<LedgerEvent> insertedEvents() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LedgerEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEventOutbox).insert(captor.capture());
        return captor.getValue();
    }

    private static List<Long> expenseIdsOf(List<LedgerEvent> events) {
        return events.stream()
                .map(event -> readJson(event.payload()).get("expenseId").asLong())
                .toList();
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private long groupingIdNamed(long userId, String name) {
        return CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, name);
    }

    private long storedCategoryId(long userId, long parentId, String name) {
        return CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, parentId, name);
    }

    /** Spending is filed under a category, never a grouping, so a leaf under a grouping of its own. */
    private long leafCategoryId(long userId, String name) {
        return storedCategoryId(userId, groupingIdNamed(userId, name + " grouping"), name);
    }

    private ExpenseEntity storedPending(
            long userId, long categoryId, String description, long minorUnits, IncomingMessageId reference) {
        return ExpenseRowUtils.storedExpense(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                description,
                null,
                minorUnits,
                "USD",
                reference.value(),
                Instant.now().minusSeconds(60),
                ExpenseStatus.PENDING);
    }

    private ExpenseEntity storedRecorded(long userId, long categoryId, String description, long minorUnits) {
        return ExpenseRowUtils.storedExpense(
                jdbcAggregateTemplate,
                userId,
                categoryId,
                description,
                null,
                minorUnits,
                "USD",
                null,
                Instant.now().minusSeconds(60),
                ExpenseStatus.RECORDED);
    }
}
