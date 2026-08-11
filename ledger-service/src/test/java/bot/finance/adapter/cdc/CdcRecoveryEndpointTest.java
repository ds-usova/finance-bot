package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.LedgerServiceApplication;
import bot.finance.common.containers.PostgresContainers;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the inbound actuator endpoint {@code POST /actuator/cdc}. Enters through the protocol - an
 * HTTP request on the management port - never by calling {@link CdcRecoveryEndpoint#recover()} directly, since an
 * actuator {@code @Endpoint} has no {@code @WebMvcTest} slice and the request's mapping, the shared-secret filter
 * ahead of it and the outcome-to-status mapping all live outside this one class's body. Only
 * {@link ChangeStreamRecovery} is mocked; the rest of the application boots for real, the same shape
 * {@code McpAdapterTest} gives the inbound MCP-tool adapter, since no lighter slice reaches an actuator endpoint.
 *
 * <p>{@code cdc.recovery-secret} is configured at the class level so {@link ChangeStreamRecovery} is genuinely
 * reachable for the happy path and the error mappings; {@link Validation.NoSecretConfigured} lays a second,
 * separately cached context over it with the secret cleared back out, the same idiom
 * {@code CdcCaptureContextTest} uses to give itself its own slot name.
 */
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@ImportTestcontainers(PostgresContainers.class)
@SpringBootTest(classes = LedgerServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "cdc.recovery-secret=" + CdcRecoveryEndpointTest.SECRET)
class CdcRecoveryEndpointTest {

    static final String SECRET = "test-recovery-secret";
    private static final String SECRET_HEADER = "X-Cdc-Recovery-Secret";
    private static final String RECOVERY_PATH = "/actuator/cdc";

    @LocalManagementPort
    private int managementPort;

    @MockitoBean
    private ChangeStreamRecovery changeStreamRecovery;

    private Response postRecovery(String headerValue) {
        RequestSpecification request = RestAssured.given()
                .port(managementPort)
                .contentType("application/json")
                .body("{}");
        if (headerValue != null) {
            request = request.header(SECRET_HEADER, headerValue);
        }
        return request.when().post(RECOVERY_PATH).then().extract().response();
    }

    @Nested
    @DisplayName("Happy Path")
    class HappyPath {

        @Test
        @DisplayName("when the recovery answers a rebuilt slot - then the response is 200 carrying both positions")
        void whenRecoveryAnswersRebuiltSlot_thenResponseIs200CarryingBothPositions() {
            when(changeStreamRecovery.recover())
                    .thenReturn(new SlotRecoveryOutcome(
                            SlotRecoveryOutcome.Status.REBUILT, Optional.of("0/1A2B3C4"), Optional.of("0/1A2B400")));

            Response response = postRecovery(SECRET);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.jsonPath().getString("abandonedPosition")).isEqualTo("0/1A2B3C4");
            assertThat(response.jsonPath().getString("resumedPosition")).isEqualTo("0/1A2B400");
            verify(changeStreamRecovery, times(1)).recover();
        }
    }

    @Nested
    @DisplayName("Error Mapping")
    class ErrorMapping {

        @Test
        @DisplayName("when the recovery refuses a slot whose wal_status is not lost - then the response is 409")
        void whenRecoveryRefusesSlotNotLost_thenResponseIs409() {
            when(changeStreamRecovery.recover())
                    .thenReturn(new SlotRecoveryOutcome(
                            SlotRecoveryOutcome.Status.SLOT_NOT_LOST, Optional.empty(), Optional.empty()));

            Response response = postRecovery(SECRET);

            assertThat(response.statusCode()).isEqualTo(409);
            verify(changeStreamRecovery, times(1)).recover();
        }

        @Test
        @DisplayName("when the recovery reports the advisory lock held - then the response is 409")
        void whenRecoveryReportsLockHeld_thenResponseIs409() {
            when(changeStreamRecovery.recover())
                    .thenReturn(new SlotRecoveryOutcome(
                            SlotRecoveryOutcome.Status.LOCK_HELD, Optional.empty(), Optional.empty()));

            Response response = postRecovery(SECRET);

            assertThat(response.statusCode()).isEqualTo(409);
            verify(changeStreamRecovery, times(1)).recover();
        }

        @Test
        @DisplayName("when the recovery reports the engine would not stop - then the response is 503")
        void whenRecoveryReportsEngineDidNotStop_thenResponseIs503() {
            when(changeStreamRecovery.recover())
                    .thenReturn(new SlotRecoveryOutcome(
                            SlotRecoveryOutcome.Status.ENGINE_DID_NOT_STOP, Optional.empty(), Optional.empty()));

            Response response = postRecovery(SECRET);

            assertThat(response.statusCode()).isEqualTo(503);
            verify(changeStreamRecovery, times(1)).recover();
        }

        @Test
        @DisplayName("when the recovery reports the position would not delete - then the response is 503")
        void whenRecoveryReportsPositionNotDeleted_thenResponseIs503() {
            when(changeStreamRecovery.recover())
                    .thenReturn(new SlotRecoveryOutcome(
                            SlotRecoveryOutcome.Status.POSITION_NOT_DELETED, Optional.empty(), Optional.empty()));

            Response response = postRecovery(SECRET);

            assertThat(response.statusCode()).isEqualTo(503);
            verify(changeStreamRecovery, times(1)).recover();
        }

        @Test
        @DisplayName("when the recovery reports the slot would not drop - then the response is 503")
        void whenRecoveryReportsSlotNotDropped_thenResponseIs503() {
            when(changeStreamRecovery.recover())
                    .thenReturn(new SlotRecoveryOutcome(
                            SlotRecoveryOutcome.Status.SLOT_NOT_DROPPED, Optional.empty(), Optional.empty()));

            Response response = postRecovery(SECRET);

            assertThat(response.statusCode()).isEqualTo(503);
            verify(changeStreamRecovery, times(1)).recover();
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("when no header is sent - then the response is 401 and the recovery never runs")
        void whenNoHeaderIsSent_thenResponseIs401AndRecoveryNeverRuns() {
            Response response = postRecovery(null);

            assertThat(response.statusCode()).isEqualTo(401);
            verify(changeStreamRecovery, never()).recover();
        }

        @Test
        @DisplayName("when the header's value differs - then the response is 401 and the recovery never runs")
        void whenHeaderValueDiffers_thenResponseIs401AndRecoveryNeverRuns() {
            Response response = postRecovery("not-the-configured-secret");

            assertThat(response.statusCode()).isEqualTo(401);
            verify(changeStreamRecovery, never()).recover();
        }

        @Test
        @DisplayName("when health is requested with no header - then it answers rather than refusing")
        void whenHealthIsRequestedWithNoHeader_thenItAnswersRatherThanRefusing() {
            Response response = RestAssured.given()
                    .port(managementPort)
                    .when()
                    .get("/actuator/health")
                    .then()
                    .extract()
                    .response();

            assertThat(response.statusCode()).isNotEqualTo(401);
            assertThat(response.jsonPath().getString("status")).isIn("UP", "DOWN");
        }

        @Test
        @DisplayName("when prometheus is requested with no header - then it answers rather than refusing")
        void whenPrometheusIsRequestedWithNoHeader_thenItAnswersRatherThanRefusing() {
            Response response = RestAssured.given()
                    .port(managementPort)
                    .when()
                    .get("/actuator/prometheus")
                    .then()
                    .extract()
                    .response();

            assertThat(response.statusCode()).isNotEqualTo(401);
        }

        @Nested
        @DisplayName("when the service has no secret configured")
        @TestPropertySource(properties = "cdc.recovery-secret=")
        class NoSecretConfigured {

            @Test
            @DisplayName("when posted with no header - then the response is 401 and the recovery never runs")
            void whenPostedWithNoHeader_thenResponseIs401AndRecoveryNeverRuns() {
                Response response = postRecovery(null);

                assertThat(response.statusCode()).isEqualTo(401);
                verify(changeStreamRecovery, never()).recover();
            }
        }
    }
}
