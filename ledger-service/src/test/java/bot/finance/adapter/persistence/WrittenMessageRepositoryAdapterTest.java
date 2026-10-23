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
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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
    private static final Instant FAR_FUTURE = Instant.parse("2200-01-01T00:00:00Z");
    private static final Instant ANCIENT = Instant.parse("2001-01-01T00:00:00Z");
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
    @DisplayName("finding the oldest waiting message filed since an instant")
    class FindOldestWaitingFiledSince {

        @Test
        @DisplayName("when texts of every state follow the instant - then the lower-id waiting one is answered")
        void whenTextsOfEveryStateFollowTheInstant_thenLowerIdWaitingOneIsAnswered() {
            Instant since = FAR_FUTURE;
            storedText("since-before", WrittenMessageStatus.WAITING, null, since.minusSeconds(60));
            storedText("since-reading", WrittenMessageStatus.READING, null, since.plusSeconds(10));
            storedText("since-done", WrittenMessageStatus.DONE, ReportOutcome.RECORDED, since.plusSeconds(20));
            long lowerId = storedText("since-lower", WrittenMessageStatus.WAITING, null, since.plusSeconds(120));
            storedText("since-higher", WrittenMessageStatus.WAITING, null, since.plusSeconds(180));

            Optional<WrittenMessageView> found = adapter.findOldestWaitingFiledSince(since);

            assertThat(found).hasValueSatisfying(view -> {
                assertThat(view.id()).isEqualTo(lowerId);
                assertThat(view.status()).isEqualTo(WrittenMessageStatus.WAITING);
                assertThat(view.filedAt()).isEqualTo(since.plusSeconds(120));
            });
        }

        @Test
        @DisplayName("when only READING and DONE texts follow the instant - then the answer is empty")
        void whenOnlyReadingAndDoneTextsFollowTheInstant_thenAnswerIsEmpty() {
            Instant since = FAR_FUTURE;
            storedText("since-none-reading", WrittenMessageStatus.READING, null, since.plusSeconds(10));
            storedText("since-none-done", WrittenMessageStatus.DONE, ReportOutcome.FAILED, since.plusSeconds(20));

            assertThat(adapter.findOldestWaitingFiledSince(since)).isEmpty();
        }
    }

    @Nested
    @DisplayName("recording an attempt")
    class RecordAttempt {

        @Test
        @DisplayName("when a waiting and a READING text are attempted - then only the waiting one's updated_at moves")
        void whenWaitingAndReadingTextsAreAttempted_thenOnlyWaitingOnesUpdatedAtMoves() {
            long waitingId = storedText("attempt-waiting", WrittenMessageStatus.WAITING, null, EARLIER);
            long readingId = storedText("attempt-reading", WrittenMessageStatus.READING, null, EARLIER);

            adapter.recordAttempt(waitingId, LATER);
            adapter.recordAttempt(readingId, LATER);

            assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, waitingId))
                    .satisfies(row -> {
                        assertThat(row.status()).isEqualTo("WAITING");
                        assertThat(row.updatedAt()).isEqualTo(LATER);
                        assertThat(row.createdAt()).isEqualTo(EARLIER);
                    });
            assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, readingId))
                    .satisfies(row -> {
                        assertThat(row.status()).isEqualTo("READING");
                        assertThat(row.updatedAt()).isEqualTo(EARLIER);
                    });
        }
    }

    @Nested
    @DisplayName("marking the hand-over")
    class MarkHandedOver {

        @Test
        @DisplayName("when the text is waiting - then it answers true and the row is READING at the instant")
        void whenTextIsWaiting_thenAnswersTrueAndRowIsReadingAtTheInstant() {
            long id = storedText("hand-waiting", WrittenMessageStatus.WAITING, null, EARLIER);

            boolean marked = adapter.markHandedOver(id, LATER);

            assertThat(marked).isTrue();
            assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, id))
                    .satisfies(row -> {
                        assertThat(row.status()).isEqualTo("READING");
                        assertThat(row.updatedAt()).isEqualTo(LATER);
                    });
        }

        @ParameterizedTest
        @EnumSource(
                value = WrittenMessageStatus.class,
                names = {"READING", "DONE"})
        @DisplayName("when the text is READING or DONE - then it answers false and the row is unchanged")
        void whenTextIsReadingOrDone_thenAnswersFalseAndRowIsUnchanged(WrittenMessageStatus status) {
            ReportOutcome outcome = status == WrittenMessageStatus.DONE ? ReportOutcome.RECORDED : null;
            long id = storedText("hand-" + status, status, outcome, EARLIER);

            boolean marked = adapter.markHandedOver(id, LATER);

            assertThat(marked).isFalse();
            assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, id))
                    .satisfies(row -> {
                        assertThat(row.status()).isEqualTo(status.name());
                        assertThat(row.updatedAt()).isEqualTo(EARLIER);
                    });
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
        @DisplayName("when the message is WAITING - then it answers true and the row is DONE with the outcome")
        void whenMessageIsWaiting_thenAnswersTrueAndRowIsDone() {
            long id = storedText("settle-waiting", WrittenMessageStatus.WAITING, null, EARLIER);

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
        @DisplayName("when the message is DONE - then it answers false and the row is unchanged")
        void whenMessageIsNotReading_thenAnswersFalseAndRowsUnchanged() {
            long doneUserId = storedUserId("written-settle-done");
            long doneId = WrittenMessageRowUtils.storedWrittenMessageId(
                    jdbcAggregateTemplate,
                    doneUserId,
                    "done",
                    WrittenMessageStatus.DONE,
                    ReportOutcome.FAILED,
                    EARLIER);

            boolean settledDone = adapter.settle(doneId, ReportOutcome.RECORDED, LATER);

            assertThat(settledDone).isFalse();
            assertThat(WrittenMessageRowUtils.writtenMessageRow(jdbcAggregateTemplate, doneId))
                    .satisfies(row -> {
                        assertThat(row.status()).isEqualTo("DONE");
                        assertThat(row.outcome()).isEqualTo("FAILED");
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
    @DisplayName("finding the waiting messages for the sweep")
    class FindWaitingForSweep {

        @Test
        @DisplayName("when texts differ in filing, touch and state - then only the stale waiting one is answered")
        void whenTextsDifferInFilingTouchAndState_thenOnlyStaleWaitingOneIsAnswered() {
            Instant filedBefore = Instant.parse("2026-10-01T10:30:00Z");
            Instant touchedBefore = Instant.parse("2026-10-01T10:20:00Z");
            long staleId = storedText(
                    "sweep-stale",
                    WrittenMessageStatus.WAITING,
                    null,
                    Instant.parse("2026-10-01T09:00:00Z"),
                    Instant.parse("2026-10-01T10:00:00Z"));
            storedText(
                    "sweep-filed-late",
                    WrittenMessageStatus.WAITING,
                    null,
                    Instant.parse("2026-10-01T10:45:00Z"),
                    Instant.parse("2026-10-01T10:00:00Z"));
            storedText(
                    "sweep-touched-late",
                    WrittenMessageStatus.WAITING,
                    null,
                    Instant.parse("2026-10-01T09:00:00Z"),
                    Instant.parse("2026-10-01T10:40:00Z"));
            storedText(
                    "sweep-reading",
                    WrittenMessageStatus.READING,
                    null,
                    Instant.parse("2026-10-01T08:00:00Z"),
                    Instant.parse("2026-10-01T08:00:00Z"));

            List<WrittenMessageView> found = adapter.findWaitingForSweep(filedBefore, touchedBefore);

            assertThat(found).extracting(WrittenMessageView::id).containsExactly(staleId);
        }
    }

    @Nested
    @DisplayName("reading the oldest waiting filing instant")
    class OldestWaitingFiledAt {

        @Test
        @DisplayName(
                "when two texts wait and an older one is READING - then the earlier waiting filing instant is read")
        void whenTwoTextsWaitAndOlderOneIsReading_thenEarlierWaitingFilingInstantIsRead() {
            storedText("oldest-reading", WrittenMessageStatus.READING, null, ANCIENT);
            storedText("oldest-later", WrittenMessageStatus.WAITING, null, ANCIENT.plusSeconds(200));
            storedText("oldest-earlier", WrittenMessageStatus.WAITING, null, ANCIENT.plusSeconds(100));

            assertThat(adapter.oldestWaitingFiledAt()).contains(ANCIENT.plusSeconds(100));
        }

        @Test
        @DisplayName("when no text waits - then the answer is empty")
        void whenNoTextWaits_thenAnswerIsEmpty() {
            storedText("oldest-none-reading", WrittenMessageStatus.READING, null, EARLIER);

            assertThat(adapter.oldestWaitingFiledAt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("against a mocked store, not the containerized database")
    class WithAMockedStore {

        private final QueryTimeoutException failure = new QueryTimeoutException("statement timed out");
        private final WrittenMessageEntityRepository mockedRepository = mock(WrittenMessageEntityRepository.class);
        private final WrittenMessageRepositoryAdapter mockedAdapter =
                new WrittenMessageRepositoryAdapter(mockedRepository);
        private final WrittenMessageRepositoryAdapter failingAdapter =
                new WrittenMessageRepositoryAdapter(mock(WrittenMessageEntityRepository.class, invocation -> {
                    throw failure;
                }));

        @Test
        @DisplayName("when store() hits a database failure - then PersistenceFailedException carries it")
        void whenStoreHitsDatabaseFailure_thenPersistenceFailedExceptionCarriesIt() {
            when(mockedRepository.save(any())).thenThrow(failure);

            assertThatThrownBy(() -> mockedAdapter.store(WrittenMessage.newMessage(1L, "coffee", EARLIER)))
                    .isInstanceOf(PersistenceFailedException.class)
                    .hasCause(failure);
        }

        @Test
        @DisplayName("when findForUser() hits a database failure - then PersistenceFailedException carries it")
        void whenFindForUserHitsDatabaseFailure_thenPersistenceFailedExceptionCarriesIt() {
            when(mockedRepository.findByIdAndUserId(any(), any())).thenThrow(failure);

            assertThatThrownBy(() -> mockedAdapter.findForUser(1L, 1L))
                    .isInstanceOf(PersistenceFailedException.class)
                    .hasCause(failure);
        }

        @Test
        @DisplayName("when settle() hits a database failure - then PersistenceFailedException carries it")
        void whenSettleHitsDatabaseFailure_thenPersistenceFailedExceptionCarriesIt() {
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

        @ParameterizedTest(name = "{0}")
        @MethodSource("sweepCalls")
        @DisplayName("when the store fails - then PersistenceFailedException carries it")
        void whenStoreFails_thenPersistenceFailedExceptionCarriesIt(
                String method, Consumer<WrittenMessageRepositoryAdapter> call) {
            assertThatThrownBy(() -> call.accept(failingAdapter))
                    .isInstanceOf(PersistenceFailedException.class)
                    .hasCause(failure);
        }

        static Stream<Arguments> sweepCalls() {
            return Stream.of(
                    call("findOldestWaitingFiledSince()", adapter -> adapter.findOldestWaitingFiledSince(EARLIER)),
                    call("recordAttempt()", adapter -> adapter.recordAttempt(1L, EARLIER)),
                    call("markHandedOver()", adapter -> adapter.markHandedOver(1L, EARLIER)),
                    call("findWaitingForSweep()", adapter -> adapter.findWaitingForSweep(LATER, EARLIER)),
                    call("oldestWaitingFiledAt()", WrittenMessageRepositoryAdapter::oldestWaitingFiledAt));
        }

        private static Arguments call(String method, Consumer<WrittenMessageRepositoryAdapter> call) {
            return Arguments.of(method, call);
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private long storedText(String tag, WrittenMessageStatus status, ReportOutcome outcome, Instant filedAt) {
        return storedText(tag, status, outcome, filedAt, filedAt);
    }

    private long storedText(
            String tag, WrittenMessageStatus status, ReportOutcome outcome, Instant filedAt, Instant updatedAt) {
        return WrittenMessageRowUtils.storedWrittenMessageId(
                jdbcAggregateTemplate,
                storedUserId("written-" + tag),
                "text " + tag,
                status,
                outcome,
                filedAt,
                updatedAt);
    }

    private List<WrittenMessageEntity> rowsOf(long userId) {
        return jdbcAggregateTemplate.findAll(WrittenMessageEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }
}
