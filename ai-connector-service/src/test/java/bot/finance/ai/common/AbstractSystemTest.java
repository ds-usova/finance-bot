package bot.finance.ai.common;

import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.grpc.test.autoconfigure.LocalGrpcServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Boots the full application over the real Netty gRPC transport on a random port, rather than the in-process
 * transport {@link GrpcAdapterTest} uses: binding the port and serving a real channel is production behaviour a
 * system test exists to prove, and the in-process transport replaces the server factory that does it.
 *
 * <p>Exposes the {@link ManagedChannel} itself, not just the {@code IntentExtractionService} stub it builds, so
 * a subclass needing a second stub — {@code HealthGrpc}, for the gRPC health check — can build one on the same
 * channel instead of opening its own.
 */
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.grpc.server.port=0")
public abstract class AbstractSystemTest {

    protected static final Logger log = LoggerFactory.getLogger(AbstractSystemTest.class);

    @LocalGrpcServerPort
    private int grpcPort;

    @LocalServerPort
    protected int actuatorPort;

    protected ManagedChannel channel;
    protected IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub intentExtractionStub;

    /**
     * Points the application's OpenAI client at the WireMock singleton. It cannot live in
     * {@code application-test.yaml} because the stub server binds a port that is only known at runtime.
     */
    @DynamicPropertySource
    static void openAiProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", WireMockSupport::openAiBaseUrl);
    }

    /**
     * Points the application's MCP client at the WireMock singleton. It cannot live in
     * {@code application-test.yaml} because the stub server binds a port that is only known at runtime.
     */
    @DynamicPropertySource
    static void ledgerMcpProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.mcp.client.streamable-http.connections.ledger.url", WireMockSupport::baseUrl);
    }

    @BeforeAll
    void openChannel() {
        channel = ManagedChannelBuilder.forAddress("localhost", grpcPort)
                .usePlaintext()
                .build();
        intentExtractionStub = IntentExtractionServiceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    void closeChannel() {
        channel.shutdownNow();
    }

    @AfterEach
    void resetStubs() {
        WireMockSupport.SERVER.resetAll();
    }
}
