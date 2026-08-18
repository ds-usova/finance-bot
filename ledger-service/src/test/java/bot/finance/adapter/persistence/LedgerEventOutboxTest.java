package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.common.boot.PersistenceAdapterTest;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PersistenceAdapterTest
@Import({LedgerEventOutbox.class, SpendingEventRenderer.class})
class LedgerEventOutboxTest {

    @Autowired
    private LedgerEventOutbox outbox;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearOutbox() {
        OutboxRowUtils.clearOutbox(jdbcTemplate);
    }

    private static SpendingRowProjection row(long id, long userId) {
        return new SpendingRowProjection(
                id,
                userId,
                "incoming-msg-" + id,
                "PENDING",
                "Milk",
                "Corner Shop",
                1500L,
                "EUR",
                Instant.parse("2026-01-01T10:00:00Z"),
                20L,
                "Groceries",
                30L,
                "Food");
    }

    @Nested
    @DisplayName("append()")
    class Append {

        @Test
        @DisplayName("when two rows are appended - then the outbox is empty again, each row having been written and "
                + "cleared")
        void whenTwoRowsAppended_thenOutboxIsEmptyAgain() {
            long userId = 5001L;

            outbox.append(
                    LedgerEventType.ProposalCreated,
                    List.of(row(1L, userId), row(2L, userId)),
                    Instant.now().truncatedTo(ChronoUnit.MICROS));

            assertThat(outbox.rowCount()).isZero();
        }

        @Test
        @DisplayName("when a row is appended beside one left behind - then only the one left behind remains")
        void whenRowAppendedBesideOneLeftBehind_thenOnlyTheOneLeftBehindRemains() {
            long userId = 5002L;
            UUID leftBehind = UUID.randomUUID();
            storedOutboxRow(leftBehind, "ProposalCreated", Instant.now().truncatedTo(ChronoUnit.MICROS), userId);

            outbox.append(LedgerEventType.ProposalAccepted, List.of(row(1L, userId)), Instant.now());

            assertThat(outboxRowsFor(userId)).extracting(OutboxRow::id).containsExactly(leftBehind);
        }

        @Test
        @DisplayName("when called with no rows - then nothing is written and no statement fails")
        void whenCalledWithNoRows_thenNothingIsWrittenAndNoStatementFails() {
            assertThatCode(() -> outbox.append(LedgerEventType.ProposalCreated, List.of(), Instant.now()))
                    .doesNotThrowAnyException();

            assertThat(outbox.rowCount()).isZero();
        }

        @Test
        @DisplayName("when called outside a transaction - then it is refused rather than committing on its own")
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void whenCalledOutsideATransaction_thenItIsRefused() {
            assertThatThrownBy(() ->
                            outbox.append(LedgerEventType.ProposalCreated, List.of(row(1L, 5003L)), Instant.now()))
                    .isInstanceOf(IllegalTransactionStateException.class);

            assertThat(outbox.rowCount()).isZero();
        }
    }

    @Nested
    @DisplayName("rowCount()")
    class RowCount {

        @Test
        @DisplayName("when one row is left in the outbox - then the count answers 1")
        void whenOneRowLeftInTheOutbox_thenCountAnswersOne() {
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
