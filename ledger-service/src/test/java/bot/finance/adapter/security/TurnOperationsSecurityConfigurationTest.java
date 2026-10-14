package bot.finance.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.EndTurnPort;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.common.boot.TurnOperationsAdapterTest;
import bot.finance.common.fixtures.IncomingMessages;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.fixtures.SessionTokens;
import bot.finance.common.fixtures.TurnOperationCalls;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Money;
import bot.finance.ledger.adapter.grpc.v1.TurnEnd;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.reflection.v1.ServerReflectionGrpc;
import io.grpc.reflection.v1.ServerReflectionRequest;
import io.grpc.reflection.v1.ServerReflectionResponse;
import io.grpc.reflection.v1.ServiceResponse;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.grpc.server.lifecycle.GrpcServerLifecycle;

/**
 * Integration test for the interceptor guarding the ledger's turn operations. Enters through the protocol - real
 * gRPC calls to the booted server - with only {@link CreateExpenseProposalPort}, {@link SummarizeSpendingPort} and
 * {@link EndTurnPort} mocked. What the operations do with an accepted call belongs to {@code LedgerTurnGrpcServiceTest}.
 */
@TurnOperationsAdapterTest
class TurnOperationsSecurityConfigurationTest {

    private static final long USER_ID = 42L;
    private static final Instant CREATED_AT = Instant.parse("2026-07-30T12:00:00Z");

    @Autowired
    private GrpcServerLifecycle grpcServerLifecycle;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private CreateExpenseProposalPort createExpenseProposalPort;

    @Autowired
    private SummarizeSpendingPort summarizeSpendingPort;

    @Autowired
    private EndTurnPort endTurnPort;

    private int port() {
        return grpcServerLifecycle.getPort();
    }

    private ManagedChannel channel() {
        return Grpc.newChannelBuilder("localhost:" + port(), InsecureChannelCredentials.create())
                .build();
    }

    private void callFileExpenseProposal(String token) {
        TurnOperationCalls.call(
                port(),
                token,
                stub -> stub.fileExpenseProposal(
                        TurnOperationCalls.fileExpense("Dining", "Cafés", "coffee", null, "4.50", "EUR")));
    }

    private void callRecordSpendingPeriod(String token) {
        TurnOperationCalls.call(
                port(),
                token,
                stub -> stub.recordSpendingPeriod(TurnOperationCalls.recordPeriod("2026-07-01", "2026-07-31")));
    }

    private void callEndTurn(String token) {
        TurnOperationCalls.endTurn(port(), token, TurnEnd.TURN_END_ACTED_ON);
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("when a token the ledger minted for a message is presented - then the call is answered")
        void whenALedgerTokenIsPresented_thenTheCallIsAnswered() {
            Expense stored = Expense.stored(
                    4242L,
                    USER_ID,
                    3L,
                    "coffee",
                    Optional.empty(),
                    new Money(450L, CurrencyCode.of("EUR")),
                    ExpenseStatus.PENDING,
                    Optional.of(IncomingMessages.newIncomingMessageId()),
                    CREATED_AT,
                    CREATED_AT);
            when(createExpenseProposalPort.create(any())).thenReturn(stored);
            String token = McpTokens.tokenFor(accessTokenMinter, USER_ID);

            assertThatCode(() -> callFileExpenseProposal(token)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when a token the ledger minted for a message is presented to EndTurn - then the call is answered")
        void whenALedgerTokenIsPresentedToEndTurn_thenTheCallIsAnswered() {
            String token = McpTokens.tokenFor(accessTokenMinter, USER_ID);

            assertThatCode(() -> callEndTurn(token)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("when the health service is asked without a credential - then it answers serving")
        void whenTheHealthServiceIsAskedWithoutACredential_thenItAnswersServing() {
            ManagedChannel channel = channel();
            try {
                HealthCheckResponse response =
                        HealthGrpc.newBlockingStub(channel).check(HealthCheckRequest.getDefaultInstance());

                assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
            } finally {
                channel.shutdownNow();
            }
        }

        @Test
        @DisplayName("when the reflection service is asked without a credential - then it names the turn service")
        void whenTheReflectionServiceIsAskedWithoutACredential_thenItNamesTheTurnService() throws Exception {
            assertThat(listedServices()).contains("bot.finance.ledger.v1.LedgerTurnService");
        }

        @Test
        @DisplayName("when the reflection service is asked without a credential - then it names the turn end service")
        void whenTheReflectionServiceIsAskedWithoutACredential_thenItNamesTheTurnEndService() throws Exception {
            assertThat(listedServices()).contains("bot.finance.ledger.v1.LedgerTurnEndService");
        }

        private List<String> listedServices() throws Exception {
            ManagedChannel channel = channel();
            try {
                CompletableFuture<ServerReflectionResponse> answer = new CompletableFuture<>();
                StreamObserver<ServerReflectionRequest> requests = ServerReflectionGrpc.newStub(channel)
                        .serverReflectionInfo(new StreamObserver<>() {
                            @Override
                            public void onNext(ServerReflectionResponse value) {
                                answer.complete(value);
                            }

                            @Override
                            public void onError(Throwable t) {
                                answer.completeExceptionally(t);
                            }

                            @Override
                            public void onCompleted() {}
                        });
                requests.onNext(ServerReflectionRequest.newBuilder()
                        .setListServices("*")
                        .build());
                requests.onCompleted();

                return answer.get(10, TimeUnit.SECONDS).getListServicesResponse().getServiceList().stream()
                        .map(ServiceResponse::getName)
                        .toList();
            } finally {
                channel.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("Error mapping")
    class ErrorMapping {

        static Stream<Arguments> refusedCredentials() {
            return Stream.of(
                    arguments("no credential", null),
                    arguments("a browser session token", SessionTokens.tokenFor(USER_ID)),
                    arguments("an expired token", McpTokens.expiredToken(USER_ID)),
                    arguments("a token of another issuer", McpTokens.wrongIssuerToken(USER_ID)),
                    arguments("a token of another audience", McpTokens.wrongAudienceToken(USER_ID)),
                    arguments("a token living past the ceiling", McpTokens.overTtlToken(USER_ID)));
        }

        static Stream<Arguments> tokensWithoutAMessage() {
            return Stream.of(
                    arguments("a token with no message reference", McpTokens.noReferenceToken(USER_ID)),
                    arguments("a token with a blank message reference", McpTokens.malformedReferenceToken(USER_ID)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("refusedCredentials")
        @DisplayName("when the credential cannot be accepted - then every operation fails unauthenticated")
        void whenTheCredentialCannotBeAccepted_thenEveryOperationFailsUnauthenticated(String credential, String token) {
            assertRefusedOnEveryOperation(token);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("tokensWithoutAMessage")
        @DisplayName("when the token carries no usable message reference - then every operation fails unauthenticated")
        void whenTheTokenCarriesNoUsableMessageReference_thenEveryOperationFailsUnauthenticated(
                String credential, String token) {
            assertRefusedOnEveryOperation(token);
        }

        private void assertRefusedOnEveryOperation(String token) {
            assertThatThrownBy(() -> callFileExpenseProposal(token))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
            assertThatThrownBy(() -> callRecordSpendingPeriod(token))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
            assertThatThrownBy(() -> callEndTurn(token))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));

            verifyNoInteractions(createExpenseProposalPort, summarizeSpendingPort, endTurnPort);
        }
    }
}
