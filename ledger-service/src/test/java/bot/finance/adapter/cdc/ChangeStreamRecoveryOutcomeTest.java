package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.adapter.persistence.ReplicationCatalogue;
import bot.finance.adapter.persistence.ReplicationSlotPosition;
import bot.finance.adapter.persistence.SlotRebuildSession;
import bot.finance.common.LogCapture;
import bot.finance.common.fixtures.CdcConfigurations;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Unit test for the statuses {@link ChangeStreamRecovery#recover()} answers. Each is decided by what the
 * collaborators say, so they are mocked and no application is booted; what needs a really-invalidated slot stays
 * in {@link ChangeStreamRecoveryTest}.
 */
class ChangeStreamRecoveryOutcomeTest {

    private static final String SLOT_NAME = "recovery_outcome_test";
    private static final String CONFIRMED_POSITION = "0/1A2B3C4";

    private ChangeStreamReader changeStreamReader;
    private ReplicationCatalogue replicationCatalogue;
    private SlotRebuildSession session;
    private ChangeStreamRecovery changeStreamRecovery;

    @BeforeEach
    void wireTheCollaborators() {
        changeStreamReader = mock(ChangeStreamReader.class);
        replicationCatalogue = mock(ReplicationCatalogue.class);
        session = mock(SlotRebuildSession.class);

        when(changeStreamReader.stop(any())).thenReturn(true);
        when(session.findSlot()).thenReturn(Optional.of(new ReplicationSlotPosition("lost", CONFIRMED_POSITION)));
        when(session.deleteStoredPosition()).thenReturn(true);
        when(session.dropSlot()).thenReturn(true);
        when(session.currentWalLsn()).thenReturn("0/9F9F9F9");
        stubTheLockHandingOverTheSession();

        changeStreamRecovery = new ChangeStreamRecovery(
                changeStreamReader,
                replicationCatalogue,
                CdcConfigurations.forSlot(SLOT_NAME),
                new Slf4jLoggerFactory());
    }

    /** The catalogue hands the sequence a session and answers whatever it returns, the way the real one does. */
    private void stubTheLockHandingOverTheSession() {
        when(replicationCatalogue.underSlotLock(eq(SLOT_NAME), any())).thenAnswer(invocation -> {
            Function<SlotRebuildSession, Object> sequence = invocation.getArgument(1);
            return Optional.of(sequence.apply(session));
        });
    }

    @Nested
    @DisplayName("recover()")
    class Recover {

        @Test
        @DisplayName("when another connection already holds the slot's lock - then the rebuild is refused")
        void whenAnotherConnectionAlreadyHoldsTheSlotsLock_thenTheRebuildIsRefused() {
            when(replicationCatalogue.underSlotLock(eq(SLOT_NAME), any())).thenReturn(Optional.empty());

            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.LOCK_HELD);
            verify(changeStreamReader, never()).stop(any());
        }

        @Test
        @DisplayName("when the slot is behind rather than lost - then the rebuild is refused and the engine runs on")
        void whenSlotIsBehindRatherThanLost_thenRebuildIsRefusedAndEngineRunsOn() {
            when(session.findSlot())
                    .thenReturn(Optional.of(new ReplicationSlotPosition("reserved", CONFIRMED_POSITION)));

            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.SLOT_NOT_LOST);
            verify(changeStreamReader, never()).stop(any());
            verify(session, never()).dropSlot();
        }

        @Test
        @DisplayName("when the engine will not stop inside the wait - then nothing is deleted or dropped")
        void whenEngineWillNotStopInsideTheWait_thenNothingIsDeletedOrDropped() {
            when(changeStreamReader.stop(any())).thenReturn(false);

            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.ENGINE_DID_NOT_STOP);
            verify(session, never()).deleteStoredPosition();
            verify(session, never()).dropSlot();
        }

        @Test
        @DisplayName("when the stored position cannot be deleted - then the slot is left alone")
        void whenStoredPositionCannotBeDeleted_thenTheSlotIsLeftAlone() {
            when(session.deleteStoredPosition()).thenReturn(false);

            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.POSITION_NOT_DELETED);
            verify(session, never()).dropSlot();
            verify(changeStreamReader, never()).start();
        }

        @Test
        @DisplayName("when the slot cannot be dropped - then the rebuild reports it and the engine is not started")
        void whenSlotCannotBeDropped_thenRebuildReportsItAndEngineIsNotStarted() {
            when(session.dropSlot()).thenReturn(false);

            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.SLOT_NOT_DROPPED);
            verify(changeStreamReader, never()).start();
        }

        /**
         * The recovery carries nothing between calls, so a refusal cannot make the next one refuse too. Written
         * against the drop because that is the refusal an operator actually retries; it guards the day someone
         * gives {@link ChangeStreamRecovery} a field remembering what the last attempt did.
         */
        @Test
        @DisplayName("when a rebuild is retried after a refusal - then nothing from the first attempt is carried")
        void whenRebuildIsRetriedAfterARefusal_thenNothingFromTheFirstAttemptIsCarried() {
            when(session.dropSlot()).thenReturn(false);
            assertThat(changeStreamRecovery.recover().status()).isEqualTo(SlotRecoveryOutcome.Status.SLOT_NOT_DROPPED);

            when(session.dropSlot()).thenReturn(true);

            assertThat(changeStreamRecovery.recover().status()).isEqualTo(SlotRecoveryOutcome.Status.REBUILT);
            verify(changeStreamReader).start();
        }

        @Test
        @DisplayName("when a lost slot is rebuilt - then the position is deleted before the slot is dropped")
        void whenLostSlotIsRebuilt_thenPositionIsDeletedBeforeSlotIsDropped() {
            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.REBUILT);
            assertThat(outcome.abandonedPosition()).contains(CONFIRMED_POSITION);
            assertThat(outcome.resumedPosition()).contains("0/9F9F9F9");

            InOrder deleteThenDrop = inOrder(session);
            deleteThenDrop.verify(session).deleteStoredPosition();
            deleteThenDrop.verify(session).dropSlot();
        }

        @Test
        @DisplayName("when no slot exists at all - then one is rebuilt with no abandoned position")
        void whenNoSlotExistsAtAll_thenOneIsRebuiltWithNoAbandonedPosition() {
            when(session.findSlot()).thenReturn(Optional.empty());

            SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

            assertThat(outcome.status()).isEqualTo(SlotRecoveryOutcome.Status.REBUILT);
            assertThat(outcome.abandonedPosition()).isEmpty();
            verify(session, never()).dropSlot();
        }

        @Test
        @DisplayName("when a lost slot is rebuilt - then the abandoned position and its time are logged at error")
        void whenLostSlotIsRebuilt_thenAbandonedPositionAndItsTimeAreLoggedAtError() {
            try (LogCapture logCapture = LogCapture.attachedTo(ChangeStreamRecovery.class)) {
                changeStreamRecovery.recover();

                List<String> messages = logCapture.messages();
                assertThat(messages).anyMatch(message -> message.toLowerCase().contains("abandon"));
                assertThat(messages).anyMatch(message -> message.contains(CONFIRMED_POSITION));
            }
        }
    }
}
