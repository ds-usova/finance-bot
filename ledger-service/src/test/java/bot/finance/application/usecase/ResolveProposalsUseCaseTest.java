package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.ProposalResolution;
import bot.finance.application.dto.ResolutionAcknowledgement;
import bot.finance.application.dto.ResolutionOutcome;
import bot.finance.application.dto.ResolveProposalsCommand;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.UserRepository;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;
import bot.finance.domain.value.MessageReference;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ResolveProposalsUseCaseTest {

    private static final long USER_ID = 1L;
    private static final String EXTERNAL_ID = "555";
    private static final String CONVERSATION_ID = "777";
    private static final String REPORT_MESSAGE_ID = "42";
    private static final String INTERACTION_ID = "interaction-1";
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-29T10:15:30Z");
    private static final MessageReference REFERENCE = MessageReference.newReference();

    private Logger log;
    private UserRepository userRepository;
    private ExpenseProposalRepository expenseProposalRepository;
    private ExpenseRepository expenseRepository;
    private MessageDeliveryPort messageDeliveryPort;
    private ResolveProposalsUseCase useCase;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(ResolveProposalsUseCase.class)).thenReturn(log);
        userRepository = mock(UserRepository.class);
        expenseProposalRepository = mock(ExpenseProposalRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        messageDeliveryPort = mock(MessageDeliveryPort.class);
        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        useCase = new ResolveProposalsUseCase(
                userRepository,
                expenseProposalRepository,
                expenseRepository,
                messageDeliveryPort,
                clock,
                loggerFactory);
    }

    private ResolveProposalsCommand newCommand(ProposalResolution resolution) {
        return new ResolveProposalsCommand(
                EXTERNAL_ID, CONVERSATION_ID, REPORT_MESSAGE_ID, INTERACTION_ID, REFERENCE, resolution);
    }

    private void stubStoredUser() {
        when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(User.stored(USER_ID, EXTERNAL_ID)));
    }

    @Nested
    @DisplayName("resolving a report")
    class Resolve {

        @Test
        @DisplayName("when resolve(null) is called - then throws InvalidIncomingMessageException and none of the "
                + "four ports is touched")
        void whenCommandIsNull_thenThrowsInvalidIncomingMessageExceptionAndPortsUntouched() {
            assertThatThrownBy(() -> useCase.resolve(null)).isInstanceOf(InvalidIncomingMessageException.class);

            verifyNoInteractions(userRepository);
            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(expenseRepository);
            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when resolve is called for an ACCEPT command and accept answers 2 - then accept is called "
                + "with the user's id, the command's reference and Instant.now(clock), and acknowledge receives a "
                + "ResolutionAcknowledgement carrying ACCEPTED, a count of 2 and the command's conversation id, "
                + "report message id and interaction id")
        void whenAcceptCommandResolvesTwo_thenAcceptCalledAndAcknowledgeReceivesAcceptedAcknowledgement() {
            stubStoredUser();
            when(expenseProposalRepository.accept(USER_ID, REFERENCE, FIXED_INSTANT))
                    .thenReturn(2);

            useCase.resolve(newCommand(ProposalResolution.ACCEPT));

            verify(expenseProposalRepository).accept(USER_ID, REFERENCE, FIXED_INSTANT);

            ArgumentCaptor<ResolutionAcknowledgement> ackCaptor =
                    ArgumentCaptor.forClass(ResolutionAcknowledgement.class);
            verify(messageDeliveryPort).acknowledge(ackCaptor.capture());
            ResolutionAcknowledgement ack = ackCaptor.getValue();
            assertThat(ack.outcome()).isEqualTo(ResolutionOutcome.ACCEPTED);
            assertThat(ack.count()).isEqualTo(2);
            assertThat(ack.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(ack.reportMessageId()).isEqualTo(REPORT_MESSAGE_ID);
            assertThat(ack.interactionId()).isEqualTo(INTERACTION_ID);
        }

        @Test
        @DisplayName("when resolve is called for a DISCARD command and discard answers 3 - then acknowledge "
                + "receives DISCARDED with a count of 3, accept is never called and ExpenseRepository is never "
                + "touched")
        void whenDiscardCommandResolvesThree_thenAcknowledgeReceivesDiscardedAndAcceptAndExpenseRepositoryUntouched() {
            stubStoredUser();
            when(expenseProposalRepository.discard(USER_ID, REFERENCE)).thenReturn(3);

            useCase.resolve(newCommand(ProposalResolution.DISCARD));

            ArgumentCaptor<ResolutionAcknowledgement> ackCaptor =
                    ArgumentCaptor.forClass(ResolutionAcknowledgement.class);
            verify(messageDeliveryPort).acknowledge(ackCaptor.capture());
            ResolutionAcknowledgement ack = ackCaptor.getValue();
            assertThat(ack.outcome()).isEqualTo(ResolutionOutcome.DISCARDED);
            assertThat(ack.count()).isEqualTo(3);

            verify(expenseProposalRepository, never()).accept(anyLong(), any(), any());
            verifyNoInteractions(expenseRepository);
        }

        @Test
        @DisplayName("when resolve is called for an ACCEPT command, accept answers 0 and countByMessageReference "
                + "answers 2 - then countByMessageReference is called with the user's id and the command's "
                + "reference, and acknowledge receives ALREADY_ACCEPTED with a count of 2")
        void whenAcceptResolvesNothingAndExpensesAlreadyStored_thenAcknowledgeReceivesAlreadyAccepted() {
            stubStoredUser();
            when(expenseProposalRepository.accept(USER_ID, REFERENCE, FIXED_INSTANT))
                    .thenReturn(0);
            when(expenseRepository.countByMessageReference(USER_ID, REFERENCE)).thenReturn(2);

            useCase.resolve(newCommand(ProposalResolution.ACCEPT));

            verify(expenseRepository).countByMessageReference(USER_ID, REFERENCE);

            ArgumentCaptor<ResolutionAcknowledgement> ackCaptor =
                    ArgumentCaptor.forClass(ResolutionAcknowledgement.class);
            verify(messageDeliveryPort).acknowledge(ackCaptor.capture());
            ResolutionAcknowledgement ack = ackCaptor.getValue();
            assertThat(ack.outcome()).isEqualTo(ResolutionOutcome.ALREADY_ACCEPTED);
            assertThat(ack.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("when resolve is called for a DISCARD command, discard answers 0 and countByMessageReference "
                + "answers 2 - then acknowledge receives ALREADY_ACCEPTED with a count of 2")
        void whenDiscardResolvesNothingAndExpensesAlreadyStored_thenAcknowledgeReceivesAlreadyAccepted() {
            stubStoredUser();
            when(expenseProposalRepository.discard(USER_ID, REFERENCE)).thenReturn(0);
            when(expenseRepository.countByMessageReference(USER_ID, REFERENCE)).thenReturn(2);

            useCase.resolve(newCommand(ProposalResolution.DISCARD));

            ArgumentCaptor<ResolutionAcknowledgement> ackCaptor =
                    ArgumentCaptor.forClass(ResolutionAcknowledgement.class);
            verify(messageDeliveryPort).acknowledge(ackCaptor.capture());
            ResolutionAcknowledgement ack = ackCaptor.getValue();
            assertThat(ack.outcome()).isEqualTo(ResolutionOutcome.ALREADY_ACCEPTED);
            assertThat(ack.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("when resolve is called, the resolution answers 0 and countByMessageReference answers 0 - "
                + "then acknowledge receives NOTHING_TO_RESOLVE with a count of 0")
        void whenResolutionAndCountBothZero_thenAcknowledgeReceivesNothingToResolve() {
            stubStoredUser();
            when(expenseProposalRepository.accept(USER_ID, REFERENCE, FIXED_INSTANT))
                    .thenReturn(0);
            when(expenseRepository.countByMessageReference(USER_ID, REFERENCE)).thenReturn(0);

            useCase.resolve(newCommand(ProposalResolution.ACCEPT));

            ArgumentCaptor<ResolutionAcknowledgement> ackCaptor =
                    ArgumentCaptor.forClass(ResolutionAcknowledgement.class);
            verify(messageDeliveryPort).acknowledge(ackCaptor.capture());
            ResolutionAcknowledgement ack = ackCaptor.getValue();
            assertThat(ack.outcome()).isEqualTo(ResolutionOutcome.NOTHING_TO_RESOLVE);
            assertThat(ack.count()).isEqualTo(0);
        }

        @Test
        @DisplayName("when findByExternalId answers empty - then acknowledge receives NOTHING_TO_RESOLVE with a "
                + "count of 0, and neither ExpenseProposalRepository nor ExpenseRepository is touched")
        void whenNoUserStoredForExternalId_thenAcknowledgeReceivesNothingToResolveAndRepositoriesUntouched() {
            when(userRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());

            useCase.resolve(newCommand(ProposalResolution.ACCEPT));

            ArgumentCaptor<ResolutionAcknowledgement> ackCaptor =
                    ArgumentCaptor.forClass(ResolutionAcknowledgement.class);
            verify(messageDeliveryPort).acknowledge(ackCaptor.capture());
            ResolutionAcknowledgement ack = ackCaptor.getValue();
            assertThat(ack.outcome()).isEqualTo(ResolutionOutcome.NOTHING_TO_RESOLVE);
            assertThat(ack.count()).isEqualTo(0);

            verifyNoInteractions(expenseProposalRepository);
            verifyNoInteractions(expenseRepository);
        }

        @Test
        @DisplayName("when a user is stored and accept throws PersistenceFailedException - then that exception "
                + "propagates and acknowledge is never called")
        void whenAcceptThrowsPersistenceFailedException_thenExceptionPropagatesAndAcknowledgeNeverCalled() {
            stubStoredUser();
            PersistenceFailedException failure = new PersistenceFailedException("move failed", new RuntimeException());
            doThrow(failure).when(expenseProposalRepository).accept(USER_ID, REFERENCE, FIXED_INSTANT);

            assertThatThrownBy(() -> useCase.resolve(newCommand(ProposalResolution.ACCEPT)))
                    .isSameAs(failure);

            verifyNoInteractions(messageDeliveryPort);
        }

        @Test
        @DisplayName("when a user is stored, discard answers 2 and acknowledge throws "
                + "MessageDeliveryFailedException - then that exception propagates")
        void whenAcknowledgeThrowsMessageDeliveryFailedException_thenExceptionPropagates() {
            stubStoredUser();
            when(expenseProposalRepository.discard(USER_ID, REFERENCE)).thenReturn(2);
            MessageDeliveryFailedException failure =
                    new MessageDeliveryFailedException("delivery failed", new RuntimeException());
            doThrow(failure).when(messageDeliveryPort).acknowledge(any());

            assertThatThrownBy(() -> useCase.resolve(newCommand(ProposalResolution.DISCARD)))
                    .isSameAs(failure);
        }
    }
}
