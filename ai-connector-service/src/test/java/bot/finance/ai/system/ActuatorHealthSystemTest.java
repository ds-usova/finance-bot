package bot.finance.ai.system;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.common.AbstractSystemTest;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ActuatorHealthSystemTest extends AbstractSystemTest {

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when GET /actuator/health is called - then returns UP with the readiness and liveness groups")
        void whenActuatorHealthIsCalled_thenReturnsUpWithReadinessAndLivenessGroups() {
            Response response = given().port(actuatorPort).when().get("/actuator/health");
            log.info("response: {}", response.getBody().asString());

            response.then().statusCode(200);
            assertThat(response.jsonPath().getString("status")).isEqualTo("UP");
            assertThat(response.jsonPath().getList("groups", String.class))
                    .containsExactlyInAnyOrder("liveness", "readiness");
        }

        @Test
        @DisplayName("when the gRPC health service is checked - then reports SERVING")
        void whenGrpcHealthServiceIsChecked_thenReportsServing() {
            HealthGrpc.HealthBlockingStub healthStub = HealthGrpc.newBlockingStub(channel);

            HealthCheckResponse response = healthStub.check(HealthCheckRequest.getDefaultInstance());
            log.info("response: {}", response);

            assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.ServingStatus.SERVING);
        }
    }
}
