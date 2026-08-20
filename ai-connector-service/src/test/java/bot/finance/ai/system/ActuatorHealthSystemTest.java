package bot.finance.ai.system;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.adapter.redis.ChangeStreamConsumer;
import bot.finance.ai.common.boot.AbstractSystemTest;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

class ActuatorHealthSystemTest extends AbstractSystemTest {

    @Autowired
    private ObjectProvider<ChangeStreamConsumer> changeStreamConsumers;

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

        @Test
        @DisplayName("when GET /actuator/health is called with the memory off - then no redis component and no "
                + "consumer bean")
        void whenActuatorHealthCalledWithMemoryOff_thenNoRedisComponentAndNoConsumerBean() {
            Response response = given().port(actuatorPort).when().get("/actuator/health");
            log.info("response: {}", response.getBody().asString());

            response.then().statusCode(200);
            assertThat(response.jsonPath().getMap("components")).doesNotContainKey("redis");
            assertThat(changeStreamConsumers.getIfAvailable()).isNull();
        }
    }
}
