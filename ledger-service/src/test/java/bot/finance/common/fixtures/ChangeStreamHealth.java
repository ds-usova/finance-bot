package bot.finance.common.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.time.Duration;

/**
 * Reads the engine's state back off {@code /actuator/health} on the management port, the way an operator sees it.
 *
 * <p>What is read is the {@code changeStream} component's own {@code state} detail, never the component's status:
 * the status is the actuator's {@code UP} or {@code DOWN}, and both {@code STREAMING} and {@code STANDBY} answer
 * {@code UP} - so reading the status would pass for {@code DOWN} by coincidence and could never match
 * {@code STREAMING} at all.
 */
public class ChangeStreamHealth {

    private static final String HEALTH_PATH = "/actuator/health";
    private static final String STATE_DETAIL = "components.changeStream.details.state";

    private ChangeStreamHealth() {}

    /** The engine's state right now, or {@code null} when the component is not registered at all. */
    public static String state(int managementPort) {
        Response health = RestAssured.given().port(managementPort).when().get(HEALTH_PATH);
        return health.jsonPath().getString(STATE_DETAIL);
    }

    /** Polls until the engine's state reads {@code expected}, the state being reached asynchronously. */
    public static void awaitState(int managementPort, String expected, Duration timeout) {
        await("the changeStream health component reads " + expected)
                .atMost(timeout)
                .untilAsserted(() -> assertThat(state(managementPort))
                        .as("changeStream engine state")
                        .isEqualTo(expected));
    }
}
