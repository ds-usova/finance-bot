package bot.finance.common.boot;

import bot.finance.common.containers.GrpcStubServer;
import bot.finance.common.containers.PostgresContainers;
import bot.finance.common.containers.WireMockSupport;
import io.restassured.response.Response;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@TheWholeApplication
public abstract class AbstractSystemTest {

    protected static final Logger log = LoggerFactory.getLogger(AbstractSystemTest.class);

    @LocalServerPort
    protected int port;

    @LocalManagementPort
    protected int managementPort;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    static {
        log.info("Postgres is running: {}", PostgresContainers.POSTGRES_CONTAINER.isRunning());
        log.info("WireMock is running on port: {}", WireMockSupport.SERVER.port());
    }

    /**
     * Points the application's Telegram client at the WireMock singleton. It cannot live in
     * {@code application-test.yaml} because the stub server binds a port that is only known at runtime.
     *
     * <p>No Telegram stub is registered here: every system test inherits this class, and {@link #tearDown()}'s
     * {@code resetAll()} would drop the stubs anyway — each Telegram test registers its own in its own
     * {@code @BeforeEach}.
     */
    @DynamicPropertySource
    static void telegramProperties(DynamicPropertyRegistry registry) {
        registry.add("telegram.bot.api-url", () -> WireMockSupport.baseUrl() + "/bot");
    }

    /**
     * Points the application at the in-JVM gRPC stub connector rather than a dead address, so the fully wired
     * application reaches it for every system test.
     */
    @DynamicPropertySource
    static void aiConnectorProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.client.channel.ai-connector.target", GrpcStubServer::target);
    }

    @PostConstruct
    void init() {
        // no op - in case we need to add something later
    }

    @BeforeAll
    static void setUpBeforeAll() {
        // no op - in case we need to add something later
    }

    @BeforeEach
    void setUp() {}

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
        GrpcStubServer.reset();
    }

    protected void logResponse(Response response) {
        log.debug("Response: {} {}", System.lineSeparator(), response.asPrettyString());
    }
}
