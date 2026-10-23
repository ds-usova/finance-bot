package bot.finance.ai.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.ai.application.dto.AcceptTurnCommand;
import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.TurnAcceptance;
import bot.finance.ai.application.dto.TurnStart;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.TurnEndPort;
import bot.finance.ai.application.port.TurnRunnerPort;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.TurnEnd;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AcceptTurnUseCaseTest {

    private static final MessageIdentity TURN_KEY = new MessageIdentity(42L, "msg-123");

    private ExtractIntentsPort extractIntentsPort;
    private TurnRunnerPort turnRunnerPort;
    private TurnEndPort turnEndPort;
    private AcceptTurnUseCase useCase;

    @BeforeEach
    void setUp() {
        extractIntentsPort = mock(ExtractIntentsPort.class);
        turnRunnerPort = mock(TurnRunnerPort.class);
        turnEndPort = mock(TurnEndPort.class);
        useCase = new AcceptTurnUseCase(extractIntentsPort, turnRunnerPort, turnEndPort);
    }

    private static ExtractIntentsCommand turn() {
        return new ExtractIntentsCommand(
                "spent 15 euros on lunch",
                List.of("Dining", "Miscellaneous"),
                "Miscellaneous",
                Optional.empty(),
                LocalDate.of(2026, 10, 13),
                Optional.of(TURN_KEY),
                List.of(
                        new CatalogueEntry("Dining", List.of("Restaurants")),
                        new CatalogueEntry("Miscellaneous", List.of("Uncategorized Expenses"))));
    }

    private void runnerAnswers(TurnStart start) {
        when(turnRunnerPort.start(any(), any(), any())).thenReturn(start);
    }

    @SuppressWarnings("unchecked")
    private Supplier<TurnEnd> handedTurn() {
        ArgumentCaptor<Supplier<TurnEnd>> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(turnRunnerPort).start(any(), captor.capture(), any());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private Consumer<TurnEnd> handedEnd() {
        ArgumentCaptor<Consumer<TurnEnd>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(turnRunnerPort).start(any(), any(), captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("accept()")
    class Accept {

        @Test
        @DisplayName("when the runner starts the turn - then ACCEPTED is answered under the command's turn key")
        void whenRunnerStartsTurn_thenAcceptedAnsweredUnderTurnKey() {
            runnerAnswers(TurnStart.STARTED);

            TurnAcceptance acceptance = useCase.accept(new AcceptTurnCommand(turn(), TURN_KEY));

            assertThat(acceptance).isEqualTo(TurnAcceptance.ACCEPTED);
            verify(turnRunnerPort).start(eq(TURN_KEY), any(), any());
        }

        @Test
        @DisplayName("when a turn under the key is already running - then ACCEPTED is answered and the body is "
                + "not called")
        void whenTurnAlreadyRunning_thenAcceptedAnsweredAndBodyNeverCalled() {
            runnerAnswers(TurnStart.ALREADY_RUNNING);

            TurnAcceptance acceptance = useCase.accept(new AcceptTurnCommand(turn(), TURN_KEY));

            assertThat(acceptance).isEqualTo(TurnAcceptance.ACCEPTED);
            verifyNoInteractions(extractIntentsPort);
        }

        @Test
        @DisplayName("when the runner is busy - then BUSY is answered and the body is not called")
        void whenRunnerBusy_thenBusyAnsweredAndBodyNeverCalled() {
            runnerAnswers(TurnStart.BUSY);

            TurnAcceptance acceptance = useCase.accept(new AcceptTurnCommand(turn(), TURN_KEY));

            assertThat(acceptance).isEqualTo(TurnAcceptance.BUSY);
            verifyNoInteractions(extractIntentsPort);
        }

        @Test
        @DisplayName("when the null command is accepted - then throws InvalidValueException and the runner is "
                + "not called")
        void whenCommandIsNull_thenThrowsInvalidValueExceptionAndRunnerNeverCalled() {
            assertThatThrownBy(() -> useCase.accept(null)).isInstanceOf(InvalidValueException.class);

            verifyNoInteractions(turnRunnerPort);
        }
    }

    @Nested
    @DisplayName("the turn handed to the runner")
    class HandedTurn {

        @Test
        @DisplayName("when the body returns - then the turn yields ACTED_ON with the command's turn extracted")
        void whenBodyReturns_thenTurnYieldsActedOn() {
            runnerAnswers(TurnStart.STARTED);
            ExtractIntentsCommand turn = turn();
            useCase.accept(new AcceptTurnCommand(turn, TURN_KEY));

            TurnEnd end = handedTurn().get();

            assertThat(end).isEqualTo(TurnEnd.ACTED_ON);
            verify(extractIntentsPort).extractIntents(turn);
        }

        @Test
        @DisplayName("when the body throws ExpenseRecordingFailedException - then the turn yields UNAVAILABLE")
        void whenBodyThrowsExpenseRecordingFailed_thenTurnYieldsUnavailable() {
            runnerAnswers(TurnStart.STARTED);
            doThrow(new ExpenseRecordingFailedException("ledger unavailable"))
                    .when(extractIntentsPort)
                    .extractIntents(any());
            useCase.accept(new AcceptTurnCommand(turn(), TURN_KEY));

            assertThat(handedTurn().get()).isEqualTo(TurnEnd.UNAVAILABLE);
        }

        @Test
        @DisplayName("when the body throws an IllegalStateException - then the turn yields FAILED")
        void whenBodyThrowsIllegalState_thenTurnYieldsFailed() {
            runnerAnswers(TurnStart.STARTED);
            doThrow(new IllegalStateException("broken"))
                    .when(extractIntentsPort)
                    .extractIntents(any());
            useCase.accept(new AcceptTurnCommand(turn(), TURN_KEY));

            assertThat(handedTurn().get()).isEqualTo(TurnEnd.FAILED);
        }

        @Test
        @DisplayName("when the runner calls the end with UNAVAILABLE - then the end port reports UNAVAILABLE")
        void whenEndCalledWithUnavailable_thenEndPortReportsUnavailable() {
            runnerAnswers(TurnStart.STARTED);
            useCase.accept(new AcceptTurnCommand(turn(), TURN_KEY));

            handedEnd().accept(TurnEnd.UNAVAILABLE);

            verify(turnEndPort).report(TurnEnd.UNAVAILABLE);
            verify(turnEndPort, never()).report(TurnEnd.ACTED_ON);
        }
    }
}
