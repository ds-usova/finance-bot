package bot.finance.adapter.aiconnector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import bot.finance.adapter.logging.TurnCallLog;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.dto.EndTurnCommand;
import bot.finance.application.dto.TurnEnding;
import bot.finance.application.port.EndTurnPort;
import bot.finance.common.LogCapture;
import bot.finance.common.boot.TurnOperationsAdapterTest;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.fixtures.TurnOperationCalls;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.ledger.adapter.grpc.v1.EndTurnResponse;
import bot.finance.ledger.adapter.grpc.v1.TurnEnd;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;

/**
 * Integration test for the inbound gRPC adapter of the turn's end. Enters through the protocol - a
 * {@code LedgerTurnEndService} call on the server's port under a bearer token - never by calling
 * {@link LedgerTurnEndGrpcService}'s methods directly. Only {@link EndTurnPort} is mocked.
 */
@TurnOperationsAdapterTest
class LedgerTurnEndGrpcServiceTest {

    private static final String INTERNAL_DETAIL = "jdbc:postgresql://db-internal:5432 refused the connection";

    @Autowired
    private GrpcServerLifecycle grpcServerLifecycle;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private EndTurnPort endTurnPort;

    private String token(long userId, String message) {
        return McpTokens.tokenFor(accessTokenMinter, userId, IncomingMessageId.of(message));
    }

    private EndTurnResponse endTurn(String token, TurnEnd end) {
        return TurnOperationCalls.endTurn(grpcServerLifecycle.getPort(), token, end);
    }

    private static StatusRuntimeException failureOf(Runnable call) {
        StatusRuntimeException failure = catchThrowableOfType(call::run, StatusRuntimeException.class);
        assertThat(failure).as("the call's failure").isNotNull();
        return failure;
    }

    private EndTurnCommand receivedCommand() {
        ArgumentCaptor<EndTurnCommand> command = ArgumentCaptor.forClass(EndTurnCommand.class);
        verify(endTurnPort).end(command.capture());
        return command.getValue();
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("when EndTurn is called with UNAVAILABLE - then the port receives the caller, the message and "
                + "the ending, and the call answers empty")
        void whenEndTurnIsCalledWithUnavailable_thenPortReceivesCallerMessageAndEndingAndCallAnswersEmpty() {
            EndTurnResponse response = endTurn(token(42L, "m"), TurnEnd.TURN_END_UNAVAILABLE);

            EndTurnCommand command = receivedCommand();
            assertThat(command.userId()).isEqualTo(new AuthenticatedUserId(42L));
            assertThat(command.reference()).isEqualTo(IncomingMessageId.of("m"));
            assertThat(command.ending()).isEqualTo(TurnEnding.UNAVAILABLE);
            assertThat(response).isEqualTo(EndTurnResponse.getDefaultInstance());
        }

        @Test
        @DisplayName("when EndTurn is called with an unspecified ending - then the port receives the ending FAILED")
        void whenEndTurnIsCalledWithUnspecifiedEnding_thenPortReceivesEndingFailed() {
            endTurn(token(42L, "m"), TurnEnd.TURN_END_UNSPECIFIED);

            assertThat(receivedCommand().ending()).isEqualTo(TurnEnding.FAILED);
        }

        @Test
        @DisplayName("when EndTurn is called - then no turn_call line is logged")
        void whenEndTurnIsCalled_thenNoTurnCallLineIsLogged() {
            try (LogCapture logCapture = LogCapture.attachedTo(TurnCallLog.class)) {
                endTurn(token(42L, "m"), TurnEnd.TURN_END_ACTED_ON);

                assertThat(logCapture.messagesStartingWith("turn_call")).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Error Mapping")
    class ErrorMapping {

        @Test
        @DisplayName("when the port throws PersistenceFailedException - then UNAVAILABLE never carries the detail")
        void whenPortThrowsPersistenceFailedException_thenUnavailableNeverCarriesTheDetail() {
            doThrow(new PersistenceFailedException(INTERNAL_DETAIL, new RuntimeException(INTERNAL_DETAIL)))
                    .when(endTurnPort)
                    .end(any());

            StatusRuntimeException failure = failureOf(() -> endTurn(token(42L, "m"), TurnEnd.TURN_END_UNAVAILABLE));

            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
            assertThat(String.valueOf(failure.getStatus().getDescription())).doesNotContain(INTERNAL_DETAIL);
        }

        @Test
        @DisplayName("when the port throws an unrecognised RuntimeException - then INTERNAL never carries the detail")
        void whenPortThrowsUnrecognisedRuntimeException_thenInternalNeverCarriesTheDetail() {
            doThrow(new IllegalStateException(INTERNAL_DETAIL))
                    .when(endTurnPort)
                    .end(any());

            StatusRuntimeException failure = failureOf(() -> endTurn(token(42L, "m"), TurnEnd.TURN_END_UNAVAILABLE));

            assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
            assertThat(String.valueOf(failure.getStatus().getDescription())).doesNotContain(INTERNAL_DETAIL);
        }
    }
}
