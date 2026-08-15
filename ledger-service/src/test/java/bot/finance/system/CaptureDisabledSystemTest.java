package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.LedgerServiceApplication;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.common.containers.PostgresContainers;
import bot.finance.common.fixtures.BrowserSessions;
import bot.finance.common.fixtures.ChangeStreamEntries;
import bot.finance.common.stubs.TelegramTestBot;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Covers {@code GET /actuator/health} and {@code /actuator/prometheus} with capture switched off, against the
 * fully wired application. The happy path needs a Postgres that cannot support logical replication at all, which
 * the shared {@code PostgresContainers} singleton is not: it runs at {@code wal_level=logical} so every other
 * capture test can reach a slot. This class starts and stops its own {@code wal_level=replica} container instead of
 * adding a second singleton nothing else needs.
 */
class CaptureDisabledSystemTest {

    @Nested
    @ActiveProfiles("test")
    @Testcontainers(disabledWithoutDocker = true)
    @SpringBootTest(
            classes = LedgerServiceApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @TestPropertySource(properties = {"cdc.enabled=false", "cdc.stream-key=capture-disabled.cdc"})
    // The container below is stopped when this class ends. A context left in the cache would keep reconnecting to
    // an address nothing listens on for the rest of the run, so it is discarded with the container it points at.
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
    @DisplayName("happy path")
    class HappyPath {

        @Container
        private static final PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>("postgres:18")
                .withDatabaseName("ledger_db")
                .withUsername("ledger_user")
                .withPassword("ledger_password")
                .withCommand("postgres", "-c", "wal_level=replica")
                .waitingFor(Wait.forListeningPort());

        /**
         * The AI connector is pointed at the in-JVM stub for the same reason {@code McpAuthenticationSystemTest}
         * does it: its health contributor reports a real connection refusal as {@code DOWN}, and the aggregate
         * {@code /actuator/health} this class asserts on would then be down for a reason it has nothing to do
         * with. This class boots on its own rather than through {@code AbstractSystemTest}, so it wires the
         * target itself.
         */
        @DynamicPropertySource
        static void containerProperties(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", POSTGRES_CONTAINER::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES_CONTAINER::getUsername);
            registry.add("spring.datasource.password", POSTGRES_CONTAINER::getPassword);
            registry.add("spring.grpc.client.channel.ai-connector.target", GrpcStubServer::target);
        }

        @LocalServerPort
        private int port;

        @LocalManagementPort
        private int managementPort;

        @Autowired
        private UserEntityRepository userEntityRepository;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @BeforeEach
        void configureRestAssured() {
            RestAssured.baseURI = "http://localhost";
            RestAssured.port = port;
        }

        @Test
        @DisplayName("when every endpoint is exercised - then no slot opens and nothing publishes")
        void whenEveryEndpointIsExercisedAndCapturedRowsChange_thenNoSlotIsOpenedAndNothingIsPublished() {
            // when: the health and metrics endpoints are exercised
            Response health = RestAssured.given().port(managementPort).when().get("/actuator/health");
            health.then().statusCode(200);
            Response metrics = RestAssured.given().port(managementPort).when().get("/actuator/prometheus");
            metrics.then().statusCode(200);

            // when: a person signs in, which writes captured category rows as part of seeding their tree
            String externalId = "capture-disabled-happy-user";
            Response signIn = BrowserSessions.signIn(TelegramTestBot.PROFILE_DEFAULT_TOKEN, externalId);
            signIn.then().statusCode(200);
            long userId = userEntityRepository
                    .findByExternalId(externalId)
                    .orElseThrow()
                    .id();

            // then: the service served every request normally
            // then: no replication slot is opened against this database
            Integer slotCount = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_replication_slots", Integer.class);
            assertThat(slotCount).as("no slot opened while capture is disabled").isZero();

            // then: nothing about the captured rows reaches the stream
            assertThat(ChangeStreamEntries.allEntriesOn("capture-disabled.cdc"))
                    .as("nothing published on this class's own stream")
                    .isEmpty();
        }
    }

    @Nested
    @ActiveProfiles("test")
    @Testcontainers(disabledWithoutDocker = true)
    @ImportTestcontainers(PostgresContainers.class)
    @SpringBootTest(
            classes = LedgerServiceApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @TestPropertySource(properties = {"cdc.enabled=false", "cdc.slot-name=capture_disabled_leftover_slot"})
    @DisplayName("unhappy path")
    class UnhappyPath {

        private static final String SLOT_NAME = "capture_disabled_leftover_slot";

        @LocalManagementPort
        private int managementPort;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @BeforeEach
        void leaveASlotBehind() {
            jdbcTemplate.execute("SELECT pg_create_logical_replication_slot('" + SLOT_NAME + "', 'pgoutput')");
        }

        @AfterEach
        void dropTheSlot() {
            jdbcTemplate.execute("SELECT pg_drop_replication_slot(slot_name) FROM pg_replication_slots "
                    + "WHERE slot_name = '" + SLOT_NAME + "'");
        }

        @Test
        @DisplayName(
                "when prometheus is scraped - then the leftover slot's metrics still report and state reads " + "down")
        void whenPrometheusIsScraped_thenLeftoverSlotMetricsStillReportAndStateReadsDown() {
            // given: a slot left behind by an earlier streaming run, with capture now off - the leftover slot is
            // created directly through SQL, since no entry point in this module creates a replication slot

            // when: /actuator/prometheus is scraped
            Response response = RestAssured.given().port(managementPort).when().get("/actuator/prometheus");
            response.then().statusCode(200);
            String body = response.getBody().asString();

            // then: the leftover slot's retained bytes and wal_status still report, and the state reads DOWN
            assertThat(body)
                    .as("retained bytes still reports the leftover slot")
                    .containsPattern(Pattern.compile("(?m)^ledger_cdc_slot_retained_bytes(\\{[^}]*})?\\s+\\S"));
            assertThat(body).as("wal status still reports the leftover slot").contains("ledger_cdc_slot_wal_status");
            assertThat(body)
                    .as("state reads down (ordinal 2)")
                    .containsPattern(Pattern.compile("(?m)^ledger_cdc_state(\\{[^}]*})?\\s+2\\.0$"));
        }
    }
}
