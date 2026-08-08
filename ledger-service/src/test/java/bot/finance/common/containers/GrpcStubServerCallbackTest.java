package bot.finance.common.containers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import com.sun.net.httpserver.HttpServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
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
 * Boots the {@code /mcp} callback mode {@link GrpcStubServer#armMcpCallbacks} arms: compiling proves nothing about
 * whether the callback actually reaches an HTTP endpoint, so this drives a real {@code extractIntents} call
 * against a throwaway HTTP server and asserts the callbacks landed there, in the order they were armed.
 */
class GrpcStubServerCallbackTest {

    private HttpServer callbackServer;

    @AfterEach
    void tearDown() {
        GrpcStubServer.reset();
        if (callbackServer != null) {
            callbackServer.stop(0);
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

            await().atMost(Duration.ofSeconds(5))
                    .untilAsserted(() -> assertThat(receivedBodies).containsExactly(firstBody, secondBody));
            assertThat(receivedAuthorization.get()).isEqualTo("Bearer test-token");
            assertThat(GrpcStubServer.mcpCallbackResponses()).hasSize(2);
        }
    }
}
