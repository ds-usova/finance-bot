package bot.finance.common;

import bot.finance.LedgerServiceApplication;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@ImportTestcontainers(PostgresContainers.class)
@SpringBootTest(
        classes = LedgerServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractSystemTest {

    protected static final Logger log = LoggerFactory.getLogger(AbstractSystemTest.class);

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    static {
        log.info("Postgres is running: {}", PostgresContainers.POSTGRES_CONTAINER.isRunning());
        log.info("WireMock is running on port: {}", WireMockSupport.SERVER.port());
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
    void setUp() {

    }

    @AfterEach
    void tearDown() {
        WireMockSupport.SERVER.resetAll();
    }

    protected void logResponse(Response response) {
        log.debug("Response: {} {}", System.lineSeparator(), response.asPrettyString());
    }

}
