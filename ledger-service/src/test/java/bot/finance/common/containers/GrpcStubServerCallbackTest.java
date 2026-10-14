package bot.finance.common.containers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import bot.finance.ledger.adapter.grpc.v1.EndTurnRequest;
import bot.finance.ledger.adapter.grpc.v1.EndTurnResponse;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnEndServiceGrpc;
import bot.finance.ledger.adapter.grpc.v1.TurnEnd;
import com.sun.net.httpserver.HttpServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Boots the {@code /mcp} callback mode {@link GrpcStubServer#armMcpCallbacks} arms and the turn end that follows an
 * answer: compiling proves nothing about whether the callback actually reaches an HTTP endpoint or the end reaches a
 * gRPC one, so this drives real {@code extractIntents} calls against a throwaway HTTP server and a throwaway
 * {@code LedgerTurnEndService} and asserts what landed there, in the order it was armed.
 */
class GrpcStubServerCallbackTest {

    private HttpServer callbackServer;
    private Server endTurnServer;

    @AfterEach
    void tearDown() {
        GrpcStubServer.reset();
        if (callbackServer != null) {
            callbackServer.stop(0);
        }
        if (endTurnServer != null) {
            endTurnServer.shutdownNow();
        }
    }

    private static Metadata authorizationHeader() {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer test-token");
        return metadata;
    }

    @Nested
    @DisplayName("calling back into /mcp with the callback mode armed")
    class ArmMcpCallbacks {

        @Test
        @DisplayName("when two callbacks are armed - then extractIntents posts both to /mcp in order, under the "
                + "caller's authorization")
        void whenExtractIntentsIsCalledWithCallbacksArmed_thenPostsArmedBodiesInOrderForwardingAuthorizationHeader()
                throws IOException {
            AtomicReference<String> receivedAuthorization = new AtomicReference<>();
            List<String> receivedBodies = new CopyOnWriteArrayList<>();
            callbackServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            callbackServer.createContext("/mcp", exchange -> {
                receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                receivedBodies.add(new String(exchange.getRequestBody().readAllBytes()));
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
            });
            callbackServer.start();
            String baseUrl = "http://localhost:" + callbackServer.getAddress().getPort();
            String firstBody = "{\"jsonrpc\":\"2.0\",\"id\":1}";
            String secondBody = "{\"jsonrpc\":\"2.0\",\"id\":2}";
            GrpcStubServer.armMcpCallbacks(baseUrl, firstBody, secondBody);

            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", GrpcStubServer.SERVER.getPort())
                    .usePlaintext()
                    .build();
            try {
                IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub stub =
                        IntentExtractionServiceGrpc.newBlockingStub(channel);
                stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authorizationHeader()))
                        .extractIntents(ExtractIntentsRequest.newBuilder()
                                .setText("test")
                                .build());
            } finally {
                channel.shutdownNow();
            }

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(receivedBodies).containsExactly(firstBody, secondBody);
                assertThat(receivedAuthorization.get()).isEqualTo("Bearer test-token");
                assertThat(GrpcStubServer.mcpCallbackResponses()).hasSize(2);
            });
        }
    }

    @Nested
    @DisplayName("ending the turn it accepts")
    class EndTurn {

        @Test
        @DisplayName("when a call is answered - then the turn is ended with the credential the call carried")
        void whenExtractIntentsIsAnswered_thenTheTurnIsEndedWithTheForwardedCredential() throws IOException {
            AtomicReference<String> receivedAuthorization = new AtomicReference<>();
            AtomicReference<TurnEnd> receivedEnd = new AtomicReference<>();
            endTurnServer = ServerBuilder.forPort(0)
                    .addService(ServerInterceptors.intercept(
                            new LedgerTurnEndServiceGrpc.LedgerTurnEndServiceImplBase() {
                                @Override
                                public void endTurn(EndTurnRequest request, StreamObserver<EndTurnResponse> observer) {
                                    receivedEnd.set(request.getEnd());
                                    observer.onNext(EndTurnResponse.getDefaultInstance());
                                    observer.onCompleted();
                                }
                            },
                            new ServerInterceptor() {
                                @Override
                                public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                                        ServerCall<ReqT, RespT> call,
                                        Metadata headers,
                                        ServerCallHandler<ReqT, RespT> next) {
                                    receivedAuthorization.set(headers.get(
                                            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)));
                                    return next.startCall(call, headers);
                                }
                            }))
                    .build()
                    .start();
            GrpcStubServer.endTurnsOn(endTurnServer.getPort());

            ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", GrpcStubServer.SERVER.getPort())
                    .usePlaintext()
                    .build();
            try {
                IntentExtractionServiceGrpc.newBlockingStub(channel)
                        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(authorizationHeader()))
                        .extractIntents(ExtractIntentsRequest.newBuilder()
                                .setText("test")
                                .build());
            } finally {
                channel.shutdownNow();
            }

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(GrpcStubServer.endTurnStatuses())
                    .containsExactly(Status.Code.OK));
            assertThat(receivedAuthorization.get()).isEqualTo("Bearer test-token");
            assertThat(receivedEnd.get()).isEqualTo(TurnEnd.TURN_END_ACTED_ON);
        }
    }
}
