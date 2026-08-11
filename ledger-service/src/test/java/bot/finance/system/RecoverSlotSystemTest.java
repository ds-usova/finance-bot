package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.CdcCaptureTest;
import bot.finance.common.fixtures.ChangeStreamEntries;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.UserRowUtils;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Covers {@code POST /actuator/cdc} end to end against the fully wired application with capture switched on,
 * entered on the management port the way an operator reaches it. The slot is invalidated the way {@code
 * ChangeStreamRecoveryTest} invalidates it - real WAL past the container's bound, forced past with a checkpoint -
 * since nothing in this module can set {@code wal_status} directly, and the engine that must stop retrying is
 * observed through {@code /actuator/health} rather than by calling {@code ChangeStreamReader} directly.
 */
@CdcCaptureTest
@TestPropertySource(
        properties = {"cdc.slot-name=recover_slot_system_test", "cdc.recovery-secret=" + RecoverSlotSystemTest.SECRET})
class RecoverSlotSystemTest {

    static final String SECRET = "recover-slot-system-test-secret";

    private static final String SLOT_NAME = "recover_slot_system_test";
    private static final String SECRET_HEADER = "X-Cdc-Recovery-Secret";
    private static final String RECOVERY_PATH = "/actuator/cdc";
    private static final int WAL_CHUNKS_PAST_THE_BOUND = 25;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName(
                "when an invalidated slot is recovered - then 200 carries both positions and streaming " + "resumes")
        void whenAnInvalidatedSlotIsRecovered_then200CarriesBothPositionsAndStreamingResumes() {
            // given: an invalidated slot and an engine that is not streaming
            awaitChangeStreamStatus("STREAMING");
            invalidateSlot();
            awaitChangeStreamStatus("DOWN");

            // when: the operation is posted to the management port with the configured secret
            Response response = RestAssured.given()
                    .port(managementPort)
                    .header(SECRET_HEADER, SECRET)
                    .when()
                    .post(RECOVERY_PATH);

            // then: the response is 200 carrying both positions
            response.then().statusCode(200);
            assertThat(response.jsonPath().getString("abandonedPosition"))
                    .as("the abandoned position")
                    .isNotBlank();
            assertThat(response.jsonPath().getString("resumedPosition"))
                    .as("the position resumed from")
                    .isNotBlank();

            // then: the health component returns to STREAMING
            awaitChangeStreamStatus("STREAMING");

            // then: a change made afterwards reaches the stream
            String externalId = "recover-slot-happy-user";
            long userId = UserRowUtils.storedUserId(userEntityRepository, externalId);
            CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, "Fees");
            await("the change made after recovery reaches the stream")
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> assertThat(ChangeStreamEntries.entriesFor("category", userId))
                            .as("category entries for the user created after recovery")
                            .isNotEmpty());
        }

        private void invalidateSlot() {
            for (int i = 0; i < WAL_CHUNKS_PAST_THE_BOUND; i++) {
                jdbcTemplate.execute("SELECT pg_logical_emit_message(true, 'test', repeat('x', 1000000))");
            }
            jdbcTemplate.execute("CHECKPOINT");
            await("the slot's wal_status reaches lost")
                    .atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(currentWalStatus()).isEqualTo("lost"));
            // a captured write after invalidation is what makes the streaming reader notice and report DOWN
            jdbcTemplate.update("UPDATE cdc_heartbeat SET beat_at = now()");
        }

        private String currentWalStatus() {
            return jdbcTemplate.queryForObject(
                    "SELECT wal_status FROM pg_replication_slots WHERE slot_name = ?", String.class, SLOT_NAME);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when health and metrics are requested with no header - then both answer on the same port")
        void whenHealthAndMetricsAreRequestedWithNoHeader_thenBothAnswerOnTheSameManagementPort() {
            // when: /actuator/health and /actuator/prometheus are requested with no header
            Response health = RestAssured.given().port(managementPort).when().get("/actuator/health");
            Response metrics = RestAssured.given().port(managementPort).when().get("/actuator/prometheus");

            // then: both answer, sharing the port the recovery operation guards
            assertThat(health.statusCode())
                    .as("health is not refused for lacking the secret")
                    .isNotEqualTo(401);
            assertThat(metrics.statusCode())
                    .as("metrics is not refused for lacking the secret")
                    .isNotEqualTo(401);
        }
    }

    private void awaitChangeStreamStatus(String expected) {
        await("the changeStream health component reads " + expected)
                .atMost(TIMEOUT)
                .untilAsserted(() -> {
                    Response health =
                            RestAssured.given().port(managementPort).when().get("/actuator/health");
                    assertThat(health.jsonPath().getString("components.changeStream.status"))
                            .as("changeStream health detail")
                            .isEqualTo(expected);
                });
    }
}
