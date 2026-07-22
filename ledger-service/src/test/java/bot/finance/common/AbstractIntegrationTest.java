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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = LedgerServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractIntegrationTest {

    protected static final Logger log = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    static {
        log.info("Postgres is running: {}", PostgresContainers.POSTGRES_CONTAINER.isRunning());
        log.info("WireMock is running on port: {}", WireMockSupport.SERVER.port());
    }

    protected static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresContainers.POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", PostgresContainers.POSTGRES_CONTAINER::getUsername);
        registry.add("spring.datasource.password", PostgresContainers.POSTGRES_CONTAINER::getPassword);
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
