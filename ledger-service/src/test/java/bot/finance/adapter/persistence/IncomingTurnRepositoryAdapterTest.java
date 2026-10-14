package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.IncomingTurnRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidIncomingTurnException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.value.IncomingMessageId;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

@PersistenceAdapterTest
@Import(IncomingTurnRepositoryAdapter.class)
class IncomingTurnRepositoryAdapterTest {

    private static final Instant EARLIER = Instant.parse("2026-10-01T10:00:00.123456Z");
    private static final Instant LATER = Instant.parse("2026-10-01T11:30:15.654321Z");
    private static final Instant FAR_FUTURE = Instant.parse("2200-01-01T00:00:00Z");
    private static final String OUTSIDE_THE_BMP = new String(Character.toChars(0x1F600));

    @Autowired
    private IncomingTurnRepositoryAdapter adapter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("keeping a turn")
    class Keep {

        @Test
        @DisplayName("when the turn is new - then it answers true and one row holds every field")
        void whenTurnIsNew_thenAnswersTrueAndOneRowHoldsEveryField() {
            long userId = storedUserId("turn-keep-new");

            boolean kept = adapter.keep(IncomingTurn.newTurn(userId, "chat-1", "msg-1", "coffee 3.40", EARLIER));

            assertThat(kept).isTrue();
            assertThat(IncomingTurnRowUtils.turnRowsFor(jdbcAggregateTemplate, userId))
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row.id()).isPositive();
                        assertThat(row.userId()).isEqualTo(userId);
                        assertThat(row.incomingMessageId()).isEqualTo("chat-1:msg-1");
                        assertThat(row.conversationId()).isEqualTo("chat-1");
                        assertThat(row.inboundMessageId()).isEqualTo("msg-1");
                        assertThat(row.text()).isEqualTo("coffee 3.40");
                        assertThat(row.keptAt()).isEqualTo(EARLIER);
                        assertThat(row.lastAttemptAt()).isNull();
                        assertThat(row.handedOverAt()).isNull();
                    });
        }

        @Test
        @DisplayName("when a turn is already kept under the message - then it answers false and one row remains")
        void whenTurnAlreadyKeptUnderTheMessage_thenAnswersFalseAndOneRowRemains() {
            long userId = storedUserId("turn-keep-twice");
            adapter.keep(IncomingTurn.newTurn(userId, "chat-2", "msg-2", "first", EARLIER));

            boolean kept = adapter.keep(IncomingTurn.newTurn(userId, "chat-2", "msg-2", "second", LATER));

            assertThat(kept).isFalse();
            assertThat(IncomingTurnRowUtils.turnRowsFor(jdbcAggregateTemplate, userId))
                    .singleElement()
                    .satisfies(row -> assertThat(row.text()).isEqualTo("first"));
        }

        @Test
        @DisplayName("when the text is 4096 UTF-16 code units ending outside the BMP - then it is stored")
        void whenTextIsExactlyAtTheLimitInCodeUnits_thenStored() {
            long userId = storedUserId("turn-keep-limit");
            String text = "a".repeat(4094) + OUTSIDE_THE_BMP;

            boolean kept = adapter.keep(IncomingTurn.newTurn(userId, "chat-3", "msg-3", text, EARLIER));

            assertThat(kept).isTrue();
            assertThat(IncomingTurnRowUtils.turnRowsFor(jdbcAggregateTemplate, userId))
                    .singleElement()
                    .satisfies(row -> assertThat(row.text()).isEqualTo(text));
        }

        @Test
        @DisplayName("when the text is 4097 UTF-16 code units - then it is refused and nothing is stored")
        void whenTextExceedsTheLimitInCodeUnits_thenRefusedAndNothingStored() {
            long userId = storedUserId("turn-keep-over-limit");
            String text = "a".repeat(4097);

            assertThatThrownBy(() -> adapter.keep(IncomingTurn.newTurn(userId, "chat-4", "msg-4", text, EARLIER)))
                    .isInstanceOf(InvalidIncomingTurnException.class);

            assertThat(IncomingTurnRowUtils.turnRowsFor(jdbcAggregateTemplate, userId))
                    .isEmpty();
        }

        @Test
        @DisplayName("when the user id names no stored user - then EntityNotFoundException is thrown")
        void whenUserIdNamesNoStoredUser_thenEntityNotFoundExceptionIsThrown() {
            assertThatThrownBy(() ->
                            adapter.keep(IncomingTurn.newTurn(999_999_999L, "chat-5", "msg-5", "coffee", EARLIER)))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("finding the oldest queued turn kept since an instant")
    class FindOldestQueuedKeptSince {

        @Test
        @DisplayName(
                "when queued and handed-over turns follow the instant - then the earlier-kept queued one is answered")
        void whenQueuedAndHandedOverTurnsFollowTheInstant_thenEarlierKeptQueuedIsAnswered() {
            long userId = storedUserId("turn-oldest-since");
            Instant since = FAR_FUTURE;
            storedTurn(userId, "before", since.minusSeconds(60), null, null);
            storedTurn(userId, "handed", since.plusSeconds(10), null, since.plusSeconds(20));
            long earlierQueuedId = storedTurn(userId, "earlier", since.plusSeconds(120), null, null);
            storedTurn(userId, "later", since.plusSeconds(180), null, null);

            Optional<IncomingTurn> found = adapter.findOldestQueuedKeptSince(since);

            assertThat(found).hasValueSatisfying(turn -> {
                assertThat(turn.id()).contains(earlierQueuedId);
                assertThat(turn.userId()).isEqualTo(userId);
                assertThat(turn.reference().value()).isEqualTo("chat-earlier:msg-earlier");
                assertThat(turn.text()).isEqualTo("text earlier");
                assertThat(turn.keptAt()).isEqualTo(since.plusSeconds(120));
                assertThat(turn.lastAttemptAt()).isEmpty();
                assertThat(turn.handedOverAt()).isEmpty();
            });
        }

        @Test
        @DisplayName("when only handed-over or deleted turns follow the instant - then the answer is empty")
        void whenOnlyHandedOverOrDeletedTurnsFollowTheInstant_thenAnswerIsEmpty() {
            long userId = storedUserId("turn-oldest-since-none");
            Instant since = FAR_FUTURE;
            storedTurn(userId, "handed", since.plusSeconds(10), null, since.plusSeconds(20));
            long deletedId = storedTurn(userId, "deleted", since.plusSeconds(30), null, null);
            jdbcAggregateTemplate.deleteById(deletedId, IncomingTurnEntity.class);

            assertThat(adapter.findOldestQueuedKeptSince(since)).isEmpty();
        }
    }

    @Nested
    @DisplayName("recording an attempt")
    class RecordAttempt {

        @Test
        @DisplayName("when the turn is queued - then its last attempt reads the instant and it stays queued")
        void whenTurnIsQueued_thenLastAttemptReadsTheInstantAndItStaysQueued() {
            long userId = storedUserId("turn-attempt");
            long id = storedTurn(userId, "attempt", EARLIER, null, null);

            adapter.recordAttempt(id, LATER);

            assertThat(IncomingTurnRowUtils.turnRow(jdbcAggregateTemplate, id)).hasValueSatisfying(row -> {
                assertThat(row.lastAttemptAt()).isEqualTo(LATER);
                assertThat(row.handedOverAt()).isNull();
                assertThat(row.keptAt()).isEqualTo(EARLIER);
            });
        }
    }

    @Nested
    @DisplayName("stamping the hand-over")
    class MarkHandedOver {

        @Test
        @DisplayName("when the turn is queued - then it answers true and the hand-over reads the instant")
        void whenTurnIsQueued_thenAnswersTrueAndHandOverReadsTheInstant() {
            long userId = storedUserId("turn-hand-queued");
            long id = storedTurn(userId, "queued", EARLIER, null, null);

            boolean stamped = adapter.markHandedOver(id, LATER);

            assertThat(stamped).isTrue();
            assertThat(IncomingTurnRowUtils.turnRow(jdbcAggregateTemplate, id))
                    .hasValueSatisfying(row -> assertThat(row.handedOverAt()).isEqualTo(LATER));
        }

        @Test
        @DisplayName("when the turn is already handed over - then it answers false and the row is unchanged")
        void whenTurnIsAlreadyHandedOver_thenAnswersFalseAndRowIsUnchanged() {
            long userId = storedUserId("turn-hand-handed");
            long id = storedTurn(userId, "handed", EARLIER, null, EARLIER);

            boolean stamped = adapter.markHandedOver(id, LATER);

            assertThat(stamped).isFalse();
            assertThat(IncomingTurnRowUtils.turnRow(jdbcAggregateTemplate, id))
                    .hasValueSatisfying(row -> assertThat(row.handedOverAt()).isEqualTo(EARLIER));
        }

        @Test
        @DisplayName("when the turn is deleted - then it answers false and no row appears")
        void whenTurnIsDeleted_thenAnswersFalseAndNoRowAppears() {
            long userId = storedUserId("turn-hand-deleted");
            long id = storedTurn(userId, "deleted", EARLIER, null, null);
            jdbcAggregateTemplate.deleteById(id, IncomingTurnEntity.class);

            boolean stamped = adapter.markHandedOver(id, LATER);

            assertThat(stamped).isFalse();
            assertThat(IncomingTurnRowUtils.turnRow(jdbcAggregateTemplate, id)).isEmpty();
        }
    }

    @Nested
    @DisplayName("finding a turn for a person")
    class FindFor {

        @Test
        @DisplayName("when the turn was kept for the person - then it is answered")
        void whenTurnWasKeptForThePerson_thenItIsAnswered() {
            long userId = storedUserId("turn-find-own");
            long id = storedTurn(userId, "own", EARLIER, null, null);

            Optional<IncomingTurn> found = adapter.findFor(userId, IncomingMessageId.of("chat-own:msg-own"));

            assertThat(found).hasValueSatisfying(turn -> {
                assertThat(turn.id()).contains(id);
                assertThat(turn.userId()).isEqualTo(userId);
                assertThat(turn.text()).isEqualTo("text own");
            });
        }

        @Test
        @DisplayName("when the turn was kept for another person - then the answer is empty")
        void whenTurnWasKeptForAnotherPerson_thenAnswerIsEmpty() {
            long userId = storedUserId("turn-find-a");
            long otherUserId = storedUserId("turn-find-b");
            storedTurn(userId, "theirs", EARLIER, null, null);

            assertThat(adapter.findFor(otherUserId, IncomingMessageId.of("chat-theirs:msg-theirs")))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("deleting a turn")
    class Delete {

        @Test
        @DisplayName("when the turn is deleted twice - then the first answers true and the second false")
        void whenTurnIsDeletedTwice_thenFirstAnswersTrueAndSecondFalse() {
            long userId = storedUserId("turn-delete");
            long id = storedTurn(userId, "delete", EARLIER, null, null);

            boolean first = adapter.delete(id);

            assertThat(first).isTrue();
            assertThat(IncomingTurnRowUtils.turnRow(jdbcAggregateTemplate, id)).isEmpty();
            assertThat(adapter.delete(id)).isFalse();
        }
    }

    @Nested
    @DisplayName("finding the turns handed over before a cutoff")
    class FindHandedOverBefore {

        @Test
        @DisplayName("when turns were handed over before and after the cutoff - then only the earlier one is answered")
        void whenTurnsWereHandedOverBeforeAndAfterTheCutoff_thenOnlyTheEarlierOneIsAnswered() {
            long userId = storedUserId("turn-handed-before");
            Instant cutoff = Instant.parse("2026-10-01T10:30:00Z");
            long beforeId = storedTurn(userId, "before", EARLIER, null, Instant.parse("2026-10-01T10:10:00Z"));
            storedTurn(userId, "after", EARLIER, null, Instant.parse("2026-10-01T10:50:00Z"));
            storedTurn(userId, "queued", EARLIER, null, null);

            List<IncomingTurn> found = adapter.findHandedOverBefore(cutoff);

            assertThat(found).extracting(turn -> turn.id().orElseThrow()).containsExactly(beforeId);
            assertThat(found.getFirst().handedOverAt()).contains(Instant.parse("2026-10-01T10:10:00Z"));
        }
    }

    @Nested
    @DisplayName("finding the queued turns for the sweep")
    class FindQueuedForSweep {

        @Test
        @DisplayName(
                "when turns differ in kept-at, attempt and hand-over - then only the stale queued ones are answered")
        void whenTurnsDifferInKeptAtAttemptAndHandOver_thenOnlyStaleQueuedOnesAreAnswered() {
            long userId = storedUserId("turn-sweep");
            Instant keptBefore = Instant.parse("2026-10-01T10:30:00Z");
            Instant attemptBefore = Instant.parse("2026-10-01T10:20:00Z");
            long attemptedId = storedTurn(
                    userId,
                    "attempted",
                    Instant.parse("2026-10-01T09:00:00Z"),
                    Instant.parse("2026-10-01T10:00:00Z"),
                    null);
            long neverAttemptedId = storedTurn(userId, "never", Instant.parse("2026-10-01T09:10:00Z"), null, null);
            storedTurn(
                    userId,
                    "kept-late",
                    Instant.parse("2026-10-01T10:45:00Z"),
                    Instant.parse("2026-10-01T10:00:00Z"),
                    null);
            storedTurn(
                    userId,
                    "attempted-late",
                    Instant.parse("2026-10-01T09:00:00Z"),
                    Instant.parse("2026-10-01T10:40:00Z"),
                    null);
            storedTurn(userId, "never-recent", Instant.parse("2026-10-01T10:25:00Z"), null, null);
            storedTurn(
                    userId,
                    "handed",
                    Instant.parse("2026-10-01T09:00:00Z"),
                    Instant.parse("2026-10-01T10:00:00Z"),
                    Instant.parse("2026-10-01T10:05:00Z"));

            List<IncomingTurn> found = adapter.findQueuedForSweep(keptBefore, attemptBefore);

            assertThat(found)
                    .extracting(turn -> turn.id().orElseThrow())
                    .containsExactlyInAnyOrder(attemptedId, neverAttemptedId);
        }
    }

    @Nested
    @DisplayName("reading the oldest queued kept-at")
    class OldestQueuedKeptAt {

        @Test
        @DisplayName(
                "when two turns are queued and an older one is handed over - then the earlier queued kept-at is read")
        void whenTwoTurnsAreQueuedAndOlderOneIsHandedOver_thenEarlierQueuedKeptAtIsRead() {
            long userId = storedUserId("turn-oldest-at");
            Instant ancient = Instant.parse("2001-01-01T00:00:00Z");
            storedTurn(userId, "handed", ancient, null, ancient.plusSeconds(5));
            storedTurn(userId, "later", ancient.plusSeconds(200), null, null);
            storedTurn(userId, "earlier", ancient.plusSeconds(100), null, null);

            assertThat(adapter.oldestQueuedKeptAt()).contains(ancient.plusSeconds(100));
        }

        @Test
        @DisplayName("when no turn is queued - then the answer is empty")
        void whenNoTurnIsQueued_thenAnswerIsEmpty() {
            long userId = storedUserId("turn-oldest-at-none");
            storedTurn(userId, "handed", EARLIER, null, LATER);

            assertThat(adapter.oldestQueuedKeptAt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("against a mocked store, not the containerized database")
    class WithAMockedStore {

        private final QueryTimeoutException failure = new QueryTimeoutException("statement timed out");
        private final IncomingTurnEntityRepository mockedRepository =
                mock(IncomingTurnEntityRepository.class, invocation -> {
                    throw failure;
                });
        private final IncomingTurnRepositoryAdapter mockedAdapter = new IncomingTurnRepositoryAdapter(mockedRepository);

        @ParameterizedTest(name = "{0}")
        @MethodSource("calls")
        @DisplayName("when the store fails - then PersistenceFailedException carries it")
        void whenStoreFails_thenPersistenceFailedExceptionCarriesIt(
                String method, Consumer<IncomingTurnRepositoryAdapter> call) {
            assertThatThrownBy(() -> call.accept(mockedAdapter))
                    .isInstanceOf(PersistenceFailedException.class)
                    .hasCause(failure);
        }

        static Stream<Arguments> calls() {
            return Stream.of(
                    call("keep()", adapter -> adapter.keep(IncomingTurn.newTurn(1L, "chat", "msg", "coffee", EARLIER))),
                    call("findOldestQueuedKeptSince()", adapter -> adapter.findOldestQueuedKeptSince(EARLIER)),
                    call("recordAttempt()", adapter -> adapter.recordAttempt(1L, EARLIER)),
                    call("markHandedOver()", adapter -> adapter.markHandedOver(1L, EARLIER)),
                    call("findFor()", adapter -> adapter.findFor(1L, IncomingMessageId.of("chat:msg"))),
                    call("delete()", adapter -> adapter.delete(1L)),
                    call("findHandedOverBefore()", adapter -> adapter.findHandedOverBefore(EARLIER)),
                    call("findQueuedForSweep()", adapter -> adapter.findQueuedForSweep(LATER, EARLIER)),
                    call("oldestQueuedKeptAt()", IncomingTurnRepositoryAdapter::oldestQueuedKeptAt));
        }

        private static Arguments call(String method, Consumer<IncomingTurnRepositoryAdapter> call) {
            return Arguments.of(method, call);
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private long storedTurn(long userId, String tag, Instant keptAt, Instant lastAttemptAt, Instant handedOverAt) {
        return IncomingTurnRowUtils.storedTurnId(
                jdbcAggregateTemplate,
                userId,
                "chat-" + tag,
                "msg-" + tag,
                "text " + tag,
                keptAt,
                lastAttemptAt,
                handedOverAt);
    }
}
