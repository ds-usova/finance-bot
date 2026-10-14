package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ReportOutcome;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.common.rows.WrittenMessageRowUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.exception.WrittenMessageInProgressException;
import bot.finance.domain.model.WrittenMessage;
import bot.finance.domain.value.WrittenMessageStatus;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@PersistenceAdapterTest
@Import(WrittenMessageRepositoryAdapter.class)
class WrittenMessageRepositoryAdapterTest {

    private static final Instant EARLIER = Instant.parse("2026-10-01T10:00:00.123456Z");
    private static final Instant LATER = Instant.parse("2026-10-01T11:30:15.654321Z");
    private static final String OUTSIDE_THE_BMP = new String(Character.toChars(0x1F600));

    @Autowired
    private WrittenMessageRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("storing a message")
    class Store {

        @Test
        @DisplayName("when the user has no message - then it is stored as WAITING with a generated id")
        void whenUserHasNoMessage_thenStoredAsWaitingWithGeneratedId() {
            long userId = storedUserId("written-store-new");

            WrittenMessageView stored = adapter.store(WrittenMessage.newMessage(userId, "coffee 3.40", EARLIER));

            assertThat(stored.id()).isPositive();
            assertThat(stored.userId()).isEqualTo(userId);
            assertThat(stored.text()).isEqualTo("coffee 3.40");
            assertThat(stored.status()).isEqualTo(WrittenMessageStatus.WAITING);
            assertThat(stored.outcome()).isEmpty();
            WrittenMessageEntity row = WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, stored.id());
            assertThat(row.text()).isEqualTo("coffee 3.40");
            assertThat(row.createdAt()).isEqualTo(EARLIER);
            assertThat(row.updatedAt()).isEqualTo(EARLIER);
            assertThat(row.outcome()).isNull();
        }

        @ParameterizedTest
        @EnumSource(
                value = WrittenMessageStatus.class,
                names = {"WAITING", "READING"})
        @DisplayName("when the user already has an unsettled message - then it is refused and one row remains")
        void whenUserHasUnsettledMessage_thenRefusedAndOneRowRemains(WrittenMessageStatus unsettled) {
            long userId = storedUserId("written-store-unsettled-" + unsettled);
            WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, "first", unsettled, null, EARLIER);

            assertThatThrownBy(() -> adapter.store(WrittenMessage.newMessage(userId, "second", LATER)))
                    .isInstanceOf(WrittenMessageInProgressException.class);

            assertThat(rowsOf(userId)).hasSize(1);
        }

        @Test
        @DisplayName("when the user's earlier message is DONE and another person's is READING - then it is stored")
        void whenEarlierMessageIsDoneAndAnotherPersonHasReading_thenStored() {
            long userId = storedUserId("written-store-after-done");
            long otherUserId = storedUserId("written-store-after-done-other");
            WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, "old", WrittenMessageStatus.DONE, ReportOutcome.RECORDED, EARLIER);
            WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, otherUserId, "theirs", WrittenMessageStatus.READING, null, EARLIER);

            WrittenMessageView stored = adapter.store(WrittenMessage.newMessage(userId, "new", LATER));

            assertThat(stored.status()).isEqualTo(WrittenMessageStatus.WAITING);
            assertThat(rowsOf(userId)).hasSize(2);
        }

        @Test
        @DisplayName("when the user id names no stored user - then EntityNotFoundException is thrown")
        void whenUserIdNamesNoStoredUser_thenEntityNotFoundExceptionIsThrown() {
            assertThatThrownBy(() -> adapter.store(WrittenMessage.newMessage(999_999_999L, "coffee", EARLIER)))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("when the text is 1000 UTF-16 code units ending outside the BMP - then it is stored")
        void whenTextIsExactlyAtTheLimitInCodeUnits_thenStored() {
            long userId = storedUserId("written-store-limit");
            String text = "a".repeat(998) + OUTSIDE_THE_BMP;

            WrittenMessageView stored = adapter.store(WrittenMessage.newMessage(userId, text, EARLIER));

            assertThat(stored.text()).isEqualTo(text);
            assertThat(rowsOf(userId)).hasSize(1);
        }

        @Test
        @DisplayName("when the text is 1001 UTF-16 code units ending outside the BMP - then it is refused")
        void whenTextExceedsTheLimitInCodeUnits_thenRefusedAndNothingStored() {
            long userId = storedUserId("written-store-over-limit");
            String text = "a".repeat(999) + OUTSIDE_THE_BMP;

            assertThatThrownBy(() -> adapter.store(WrittenMessage.newMessage(userId, text, EARLIER)))
                    .isInstanceOf(InvalidWrittenMessageException.class);

            assertThat(rowsOf(userId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("finding a message for a user")
    class FindForUser {

        @Test
        @DisplayName("when the caller's message is DONE with PARTIAL - then it is answered with both")
        void whenCallersMessageIsDone_thenAnsweredWithItsOutcome() {
            long userId = storedUserId("written-find-own");
            long id = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate,
                    userId,
                    "coffee 3.40",
                    WrittenMessageStatus.DONE,
                    ReportOutcome.PARTIAL,
                    EARLIER);

            Optional<WrittenMessageView> found = adapter.findForUser(userId, id);

            assertThat(found).hasValueSatisfying(view -> {
                assertThat(view.id()).isEqualTo(id);
                assertThat(view.text()).isEqualTo("coffee 3.40");
                assertThat(view.status()).isEqualTo(WrittenMessageStatus.DONE);
                assertThat(view.outcome()).contains(ReportOutcome.PARTIAL);
            });
        }

        @Test
        @DisplayName("when the message belongs to another person - then the answer is empty")
        void whenMessageBelongsToAnotherPerson_thenAnswerIsEmpty() {
            long userId = storedUserId("written-find-caller");
            long otherUserId = storedUserId("written-find-other");
            long id = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, otherUserId, "theirs", WrittenMessageStatus.WAITING, null, EARLIER);

            assertThat(adapter.findForUser(userId, id)).isEmpty();
        }

        @Test
        @DisplayName("when the id names nothing - then the answer is empty")
        void whenIdNamesNothing_thenAnswerIsEmpty() {
            long userId = storedUserId("written-find-nothing");

            assertThat(adapter.findForUser(userId, 999_999_999L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("finding the unsettled messages of a user")
    class FindUnsettledForUser {

        @Test
        @DisplayName("when the caller holds one DONE and one WAITING message - then the WAITING one alone is answered")
        void whenCallerHoldsDoneAndWaiting_thenWaitingAloneIsAnswered() {
            long userId = storedUserId("written-unsettled-caller");
            long otherUserId = storedUserId("written-unsettled-other");
            WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, "done", WrittenMessageStatus.DONE, ReportOutcome.RECORDED, EARLIER);
            long waitingId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, "waiting", WrittenMessageStatus.WAITING, null, LATER);
            WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, otherUserId, "theirs", WrittenMessageStatus.READING, null, LATER);

            List<WrittenMessageView> unsettled = adapter.findUnsettledForUser(userId);

            assertThat(unsettled).extracting(WrittenMessageView::id).containsExactly(waitingId);
            assertThat(unsettled.getFirst().status()).isEqualTo(WrittenMessageStatus.WAITING);
        }
    }

    @Nested
    @DisplayName("claiming the oldest waiting message")
    class ClaimOldestWaiting {

        @Test
        @Disabled("RI03: the claim gives way to the attempt and the hand-over stamp")
        @DisplayName("when two messages wait - then the older one is answered as READING and the other stays WAITING")
        void whenTwoMessagesWait_thenOlderIsClaimed() {
            // long firstUserId = storedUserId("written-claim-first");
            // long secondUserId = storedUserId("written-claim-second");
            // long readingUserId = storedUserId("written-claim-reading");
            // long olderId = WrittenMessageRowUtils.storedWrittenMessageId(
            //         jdbcAggregateTemplate, firstUserId, "older", WrittenMessageStatus.WAITING, null, EARLIER);
            // long newerId = WrittenMessageRowUtils.storedWrittenMessageId(
            //         jdbcAggregateTemplate, secondUserId, "newer", WrittenMessageStatus.WAITING, null, EARLIER);
            // WrittenMessageRowUtils.storedWrittenMessageId(
            //         jdbcAggregateTemplate, readingUserId, "busy", WrittenMessageStatus.READING, null, EARLIER);
            //
            // Optional<WrittenMessageView> claimed = adapter.claimOldestWaiting(LATER);
            //
            // assertThat(claimed).hasValueSatisfying(view -> {
            //     assertThat(view.id()).isEqualTo(olderId);
            //     assertThat(view.status()).isEqualTo(WrittenMessageStatus.READING);
            // });
            // assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, olderId))
            //         .satisfies(row -> {
            //             assertThat(row.status()).isEqualTo("READING");
            //             assertThat(row.updatedAt()).isEqualTo(LATER);
            //         });
            // assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, newerId)
            //                 .status())
            //         .isEqualTo("WAITING");
        }

        @Test
        @Disabled("RI03: the claim gives way to the attempt and the hand-over stamp")
        @DisplayName("when no message waits - then the answer is empty")
        void whenNoMessageWaits_thenAnswerIsEmpty() {
            // long userId = storedUserId("written-claim-none");
            // WrittenMessageRowUtils.storedWrittenMessageId(
            //         jdbcAggregateTemplate, userId, "busy", WrittenMessageStatus.READING, null, EARLIER);
            //
            // assertThat(adapter.claimOldestWaiting(LATER)).isEmpty();
        }
    }

    @Nested
    @DisplayName("stamping the hand-over")
    class StampHandOver {

        @Test
        @Disabled("RI03: the stamp gives way to the hand-over mark")
        @DisplayName("when the message is READING - then it answers true and moves updated_at to the instant")
        void whenMessageIsReading_thenAnswersTrueAndStamps() {
            // long userId = storedUserId("written-stamp-reading");
            // long id = WrittenMessageRowUtils.storedWrittenMessageId(
            //         jdbcAggregateTemplate, userId, "text", WrittenMessageStatus.READING, null, EARLIER);
            //
            // boolean stamped = adapter.stampHandOver(id, LATER);
            //
            // assertThat(stamped).isTrue();
            // assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, id)
            //                 .updatedAt())
            //         .isEqualTo(LATER);
        }

        @Test
        @Disabled("RI03: the stamp gives way to the hand-over mark")
        @DisplayName("when the message is DONE - then it answers false and the row is unchanged")
        void whenMessageIsDone_thenAnswersFalseAndRowUnchanged() {
            // long userId = storedUserId("written-stamp-done");
            // long id = WrittenMessageRowUtils.storedWrittenMessageId(
            //         jdbcAggregateTemplate, userId, "text", WrittenMessageStatus.DONE, ReportOutcome.RECORDED,
            // EARLIER);
            //
            // boolean stamped = adapter.stampHandOver(id, LATER);
            //
            // assertThat(stamped).isFalse();
            // assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, id))
            //         .satisfies(row -> {
            //             assertThat(row.status()).isEqualTo("DONE");
            //             assertThat(row.updatedAt()).isEqualTo(EARLIER);
            //         });
        }
    }

    @Nested
    @DisplayName("settling a message")
    class Settle {

        @Test
        @DisplayName("when the message is READING - then it answers true and the row is DONE with the outcome")
        void whenMessageIsReading_thenAnswersTrueAndRowIsDone() {
            long userId = storedUserId("written-settle-reading");
            long id = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, userId, "text", WrittenMessageStatus.READING, null, EARLIER);

            boolean settled = adapter.settle(id, ReportOutcome.RECORDED, LATER);

            assertThat(settled).isTrue();
            assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, id))
                    .satisfies(row -> {
                        assertThat(row.status()).isEqualTo("DONE");
                        assertThat(row.outcome()).isEqualTo("RECORDED");
                        assertThat(row.updatedAt()).isEqualTo(LATER);
                    });
        }

        @Test
        @DisplayName("when the message is DONE or still WAITING - then it answers false and the rows are unchanged")
        void whenMessageIsNotReading_thenAnswersFalseAndRowsUnchanged() {
            long doneUserId = storedUserId("written-settle-done");
            long waitingUserId = storedUserId("written-settle-waiting");
            long doneId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate,
                    doneUserId,
                    "done",
                    WrittenMessageStatus.DONE,
                    ReportOutcome.FAILED,
                    EARLIER);
            long waitingId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate, waitingUserId, "waiting", WrittenMessageStatus.WAITING, null, EARLIER);

            boolean settledDone = adapter.settle(doneId, ReportOutcome.RECORDED, LATER);
            boolean settledWaiting = adapter.settle(waitingId, ReportOutcome.RECORDED, LATER);

            assertThat(settledDone).isFalse();
            assertThat(settledWaiting).isFalse();
            assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, doneId))
                    .satisfies(row -> {
                        assertThat(row.status()).isEqualTo("DONE");
                        assertThat(row.outcome()).isEqualTo("FAILED");
                        assertThat(row.updatedAt()).isEqualTo(EARLIER);
                    });
            assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, waitingId))
                    .satisfies(row -> {
                        assertThat(row.status()).isEqualTo("WAITING");
                        assertThat(row.outcome()).isNull();
                        assertThat(row.updatedAt()).isEqualTo(EARLIER);
                    });
        }
    }

    @Nested
    @DisplayName("finding the READING messages updated before a cutoff")
    class FindReadingUpdatedBefore {

        @Test
        @DisplayName(
                "when messages differ in state and age - then only the READING one updated before the cutoff is answered")
        void whenMessagesDifferInStateAndAge_thenOnlyStaleReadingIsAnswered() {
            Instant cutoff = Instant.parse("2026-10-01T10:30:00Z");
            Instant before = Instant.parse("2026-10-01T10:00:00Z");
            Instant after = Instant.parse("2026-10-01T11:00:00Z");
            long staleId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate,
                    storedUserId("written-stale-reading"),
                    "stale",
                    WrittenMessageStatus.READING,
                    null,
                    before);
            WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate,
                    storedUserId("written-fresh-reading"),
                    "fresh",
                    WrittenMessageStatus.READING,
                    null,
                    after);
            WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate,
                    storedUserId("written-old-waiting"),
                    "waiting",
                    WrittenMessageStatus.WAITING,
                    null,
                    before);
            WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate,
                    storedUserId("written-old-done"),
                    "done",
                    WrittenMessageStatus.DONE,
                    ReportOutcome.RECORDED,
                    before);

            List<WrittenMessageView> stale = adapter.findReadingUpdatedBefore(cutoff);

            assertThat(stale).extracting(WrittenMessageView::id).containsExactly(staleId);
        }
    }

    @Nested
    @DisplayName("against a mocked store, not the containerized database")
    class WithAMockedStore {

        private final WrittenMessageEntityRepository mockedRepository = mock(WrittenMessageEntityRepository.class);
        private final WrittenMessageRepositoryAdapter mockedAdapter =
                new WrittenMessageRepositoryAdapter(mockedRepository);

        @Test
        @DisplayName("when store() hits a database failure - then PersistenceFailedException carries it")
        void whenStoreHitsDatabaseFailure_thenPersistenceFailedExceptionCarriesIt() {
            QueryTimeoutException failure = new QueryTimeoutException("statement timed out");
            when(mockedRepository.save(any())).thenThrow(failure);

            assertThatThrownBy(() -> mockedAdapter.store(WrittenMessage.newMessage(1L, "coffee", EARLIER)))
                    .isInstanceOf(PersistenceFailedException.class)
                    .hasCause(failure);
        }

        @Test
        @DisplayName("when findForUser() hits a database failure - then PersistenceFailedException carries it")
        void whenFindForUserHitsDatabaseFailure_thenPersistenceFailedExceptionCarriesIt() {
            QueryTimeoutException failure = new QueryTimeoutException("statement timed out");
            when(mockedRepository.findByIdAndUserId(any(), any())).thenThrow(failure);

            assertThatThrownBy(() -> mockedAdapter.findForUser(1L, 1L))
                    .isInstanceOf(PersistenceFailedException.class)
                    .hasCause(failure);
        }

        @Test
        @Disabled("RI03: the claim gives way to the attempt and the hand-over stamp")
        @DisplayName("when claimOldestWaiting() hits a database failure - then PersistenceFailedException carries it")
        void whenClaimOldestWaitingHitsDatabaseFailure_thenPersistenceFailedExceptionCarriesIt() {
            // QueryTimeoutException failure = new QueryTimeoutException("statement timed out");
            // when(mockedRepository.claimOldestWaiting(any())).thenThrow(failure);
            //
            // assertThatThrownBy(() -> mockedAdapter.claimOldestWaiting(EARLIER))
            //         .isInstanceOf(PersistenceFailedException.class)
            //         .hasCause(failure);
        }

        @Test
        @DisplayName("when settle() hits a database failure - then PersistenceFailedException carries it")
        void whenSettleHitsDatabaseFailure_thenPersistenceFailedExceptionCarriesIt() {
            QueryTimeoutException failure = new QueryTimeoutException("statement timed out");
            when(mockedRepository.settle(any(), any(), any())).thenThrow(failure);

            assertThatThrownBy(() -> mockedAdapter.settle(1L, ReportOutcome.RECORDED, EARLIER))
                    .isInstanceOf(PersistenceFailedException.class)
                    .hasCause(failure);
        }

        @Test
        @DisplayName(
                "when store() hits a constraint violation it does not classify - then PersistenceFailedException carries it")
        void whenStoreHitsUnclassifiedConstraintViolation_thenPersistenceFailedExceptionCarriesIt() {
            SQLException sqlException = new SQLException(
                    "ERROR: duplicate key value violates unique constraint \"some_other_index\"", "23505");
            DataIntegrityViolationException failure =
                    new DataIntegrityViolationException("constraint violation", sqlException);
            when(mockedRepository.save(any())).thenThrow(failure);

            assertThatThrownBy(() -> mockedAdapter.store(WrittenMessage.newMessage(1L, "coffee", EARLIER)))
                    .isInstanceOf(PersistenceFailedException.class)
                    .hasCause(failure);
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private List<WrittenMessageEntity> rowsOf(long userId) {
        return jdbcAggregateTemplate.findAll(WrittenMessageEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }
}
