package bot.finance.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.InitializeUserCommand;
import bot.finance.application.port.IncomingTurnRepository;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.TurnHandOverDispatchPort;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.IncomingTurn;
import bot.finance.domain.model.User;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class HandleIncomingMessageUseCaseTest {

    private static final long USER_ID = 1L;
    private static final String EXTERNAL_ID = "555";
    private static final String CONVERSATION_ID = "777";
    private static final String INBOUND_MESSAGE_ID = "1";
    private static final String TEXT = "spent 12 on coffee";
    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");

    private Logger log;
    private InitializeUserPort initializeUserPort;
    private IncomingTurnRepository incomingTurnRepository;
    private TurnHandOverDispatchPort turnHandOverDispatchPort;
    private HandleIncomingMessageUseCase useCase;

    @BeforeEach
    void setUp() {
        log = mock(Logger.class);
        LoggerFactory loggerFactory = mock(LoggerFactory.class);
        when(loggerFactory.getLogger(HandleIncomingMessageUseCase.class)).thenReturn(log);
        initializeUserPort = mock(InitializeUserPort.class);
        incomingTurnRepository = mock(IncomingTurnRepository.class);
        turnHandOverDispatchPort = mock(TurnHandOverDispatchPort.class);
        useCase = new HandleIncomingMessageUseCase(
                initializeUserPort,
                incomingTurnRepository,
                turnHandOverDispatchPort,
                Clock.fixed(NOW, ZoneOffset.UTC),
                loggerFactory);
        when(initializeUserPort.initialize(any())).thenReturn(User.stored(USER_ID, EXTERNAL_ID));
        when(incomingTurnRepository.keep(any())).thenReturn(true);
    }

    private HandleIncomingMessageCommand command() {
        return new HandleIncomingMessageCommand(EXTERNAL_ID, CONVERSATION_ID, INBOUND_MESSAGE_ID, TEXT);
    }

    private IncomingTurn keptTurn() {
        ArgumentCaptor<IncomingTurn> captor = ArgumentCaptor.forClass(IncomingTurn.class);
        verify(incomingTurnRepository).keep(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("handling an incoming message")
    class Handle {

        @Test
        @DisplayName("when the command is null - then throws InvalidIncomingMessageException and no port is called")
        void whenCommandIsNull_thenThrowsInvalidIncomingMessageExceptionAndNoPortIsCalled() {
            assertThatThrownBy(() -> useCase.handle(null)).isInstanceOf(InvalidIncomingMessageException.class);

            verifyNoInteractions(log);
            verifyNoInteractions(initializeUserPort);
            verifyNoInteractions(incomingTurnRepository);
            verifyNoInteractions(turnHandOverDispatchPort);
        }

        @Test
        @DisplayName("when a message is handled - then initialize receives the command's user external id")
        void whenAMessageIsHandled_thenInitializeReceivesTheCommandsUserExternalId() {
            useCase.handle(command());

            ArgumentCaptor<InitializeUserCommand> captor = ArgumentCaptor.forClass(InitializeUserCommand.class);
            verify(initializeUserPort).initialize(captor.capture());
            assertThat(captor.getValue().externalId()).isEqualTo(EXTERNAL_ID);
        }

        @Test
        @DisplayName("when a message is handled - then the store keeps a queued turn for the person")
        void whenAMessageIsHandled_thenTheStoreKeepsAQueuedTurnForThePerson() {
            useCase.handle(command());

            IncomingTurn kept = keptTurn();
            assertThat(kept.userId()).isEqualTo(USER_ID);
            assertThat(kept.reference()).isEqualTo(IncomingMessageId.of("777:1"));
            assertThat(kept.text()).isEqualTo(TEXT);
            assertThat(kept.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(kept.inboundMessageId()).isEqualTo(INBOUND_MESSAGE_ID);
            assertThat(kept.keptAt()).isEqualTo(NOW);
            assertThat(kept.lastAttemptAt()).isEmpty();
            assertThat(kept.handedOverAt()).isEmpty();
        }

        @Test
        @DisplayName("when the store keeps the turn - then the hand-over is dispatched once, after the keep")
        void whenTheStoreKeepsTheTurn_thenTheHandOverIsDispatchedOnceAfterTheKeep() {
            useCase.handle(command());

            InOrder order = inOrder(incomingTurnRepository, turnHandOverDispatchPort);
            order.verify(incomingTurnRepository).keep(any(IncomingTurn.class));
            order.verify(turnHandOverDispatchPort).dispatch();
            order.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("when a turn is already kept under the message - then nothing is dispatched")
        void whenATurnIsAlreadyKeptUnderTheMessage_thenNothingIsDispatched() {
            when(incomingTurnRepository.keep(any())).thenReturn(false);

            useCase.handle(command());

            verify(incomingTurnRepository).keep(any(IncomingTurn.class));
            verify(turnHandOverDispatchPort, never()).dispatch();
        }

        @Test
        @DisplayName("when a turn is already kept under the message - then one info line names the message")
        void whenATurnIsAlreadyKeptUnderTheMessage_thenOneInfoLineNamesTheMessage() {
            when(incomingTurnRepository.keep(any())).thenReturn(false);

            useCase.handle(command());

            ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
            verify(log).info(message.capture(), args.capture());
            String line = message.getValue() + " "
                    + Arrays.stream(args.getValue()).map(String::valueOf).collect(Collectors.joining(" "));
            assertThat(line).contains("777:1").doesNotContain(TEXT);
        }

        @Test
        @DisplayName("when the store throws PersistenceFailedException - then it propagates and nothing is dispatched")
        void whenTheStoreThrowsPersistenceFailedException_thenItPropagatesAndNothingIsDispatched() {
            PersistenceFailedException failure = new PersistenceFailedException("write failed", new RuntimeException());
            when(incomingTurnRepository.keep(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(command())).isSameAs(failure);

            verify(incomingTurnRepository).keep(any(IncomingTurn.class));
            verify(turnHandOverDispatchPort, never()).dispatch();
        }

        @Test
        @DisplayName("when initialize throws PersistenceFailedException - then it propagates and nothing is kept")
        void whenInitializeThrowsPersistenceFailedException_thenItPropagatesAndNothingIsKept() {
            PersistenceFailedException failure =
                    new PersistenceFailedException("lookup failed", new RuntimeException());
            when(initializeUserPort.initialize(any())).thenThrow(failure);

            assertThatThrownBy(() -> useCase.handle(command())).isSameAs(failure);

            verify(initializeUserPort).initialize(any(InitializeUserCommand.class));
            verifyNoInteractions(incomingTurnRepository);
            verifyNoInteractions(turnHandOverDispatchPort);
        }
    }
}
