package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.fixtures.JsonUtils;
import bot.finance.common.rows.OutboxRowUtils;
import bot.finance.common.rows.OutboxRowUtils.OutboxRow;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@PersistenceAdapterTest
@Import(LedgerEventOutbox.class)
class LedgerEventOutboxTest {

    @Autowired
    private LedgerEventOutbox outbox;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearOutbox() {
        OutboxRowUtils.clearOutbox(jdbcTemplate);
    }

    @Nested
    @DisplayName("inserting events")
    class Insert {

        @Test
        @DisplayName(
                "when two events with distinct ids, types, instants and payloads are inserted - then outbox holds both rows")
        void whenTwoDistinctEventsInserted_thenOutboxHoldsBothRowsWithTheirOwnFields() {
            long userId = 5001L;
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            Instant firstOccurredAt = Instant.now().minusSeconds(120).truncatedTo(ChronoUnit.MICROS);
            Instant secondOccurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            String firstPayload = "{\"userId\":%d,\"expenseId\":1}".formatted(userId);
            String secondPayload = "{\"userId\":%d,\"expenseId\":2}".formatted(userId);
            LedgerEvent first = new LedgerEvent(firstId, "ProposalCreated", firstOccurredAt, firstPayload);
            LedgerEvent second = new LedgerEvent(secondId, "ExpenseRecorded", secondOccurredAt, secondPayload);

            outbox.insert(List.of(first, second));

            List<OutboxRow> rows = outboxRowsFor(userId);
            assertThat(rows).hasSize(2);
            assertThat(rows)
                    .filteredOn(row -> row.id().equals(firstId))
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row.type()).isEqualTo("ProposalCreated");
                        assertThat(row.occurredAt()).isEqualTo(firstOccurredAt);
                        assertThat(JsonUtils.readJson(row.payload())
                                        .get("expenseId")
                                        .asInt())
                                .isEqualTo(1);
                    });
            assertThat(rows)
                    .filteredOn(row -> row.id().equals(secondId))
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row.type()).isEqualTo("ExpenseRecorded");
                        assertThat(row.occurredAt()).isEqualTo(secondOccurredAt);
                        assertThat(JsonUtils.readJson(row.payload())
                                        .get("expenseId")
                                        .asInt())
                                .isEqualTo(2);
                    });
        }

        @Test
        @DisplayName("when called with an empty list - then nothing is written and no statement fails")
        void whenCalledWithEmptyList_thenNothingIsWrittenAndNoStatementFails() {
            assertThatCode(() -> outbox.insert(List.of())).doesNotThrowAnyException();

            assertThat(outbox.rowCount()).isZero();
        }
    }

    @Nested
    @DisplayName("deleting events")
    class Delete {

        @Test
        @DisplayName("when two of three stored ids are deleted - then only the third row remains")
        void whenTwoOfThreeStoredIdsDeleted_thenOnlyThirdRowRemains() {
            long userId = 5002L;
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            UUID thirdId = UUID.randomUUID();
            Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            storedOutboxRow(firstId, "ProposalCreated", occurredAt, userId);
            storedOutboxRow(secondId, "ProposalAccepted", occurredAt, userId);
            storedOutboxRow(thirdId, "ProposalDiscarded", occurredAt, userId);

            outbox.delete(List.of(firstId, secondId));

            List<OutboxRow> rows = outboxRowsFor(userId);
            assertThat(rows).extracting(OutboxRow::id).containsExactly(thirdId);
        }

        @Test
        @DisplayName("when called with an empty list of ids - then nothing is removed and no statement fails")
        void whenCalledWithEmptyListOfIds_thenNothingIsRemovedAndNoStatementFails() {
            long userId = 5003L;
            storedOutboxRow(UUID.randomUUID(), "ProposalCreated", Instant.now().truncatedTo(ChronoUnit.MICROS), userId);

            assertThatCode(() -> outbox.delete(List.of())).doesNotThrowAnyException();

            assertThat(outboxRowsFor(userId)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("counting rows")
    class RowCount {

        @Test
        @DisplayName("when one row is inserted into outbox - then the count answers 1")
        void whenOneRowInserted_thenCountAnswersOne() {
            storedOutboxRow(UUID.randomUUID(), "ProposalCreated", Instant.now().truncatedTo(ChronoUnit.MICROS), 5004L);

            assertThat(outbox.rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("when the table is empty - then the count answers 0")
        void whenTableIsEmpty_thenCountAnswersZero() {
            assertThat(outbox.rowCount()).isZero();
        }
    }

    private List<OutboxRow> outboxRowsFor(long userId) {
        return OutboxRowUtils.outboxRowsFor(jdbcTemplate, userId);
    }

    private void storedOutboxRow(UUID id, String type, Instant occurredAt, long userId) {
        OutboxRowUtils.storedOutboxRowFor(jdbcTemplate, id, type, occurredAt, userId);
    }
}
