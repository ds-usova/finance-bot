package bot.finance.adapter.aiconnector;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.boot.AiConnectorAdapterTest;
import bot.finance.common.containers.GrpcStubServer;
import io.grpc.Status;
import io.grpc.health.v1.HealthCheckResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;

@AiConnectorAdapterTest
class AiConnectorHealthIndicatorTest {

    @Autowired
    private AiConnectorHealthIndicator healthIndicator;

    @AfterEach
    void resetStubServer() {
        GrpcStubServer.reset();
    }

    @Nested
    @DisplayName("checking health")
    class HealthCheck {

        @Test
        @DisplayName("when the stub server reports SERVING - then the status is UP")
        void whenStubServerReportsServing_thenStatusIsUp() {
            GrpcStubServer.reportServingStatus(HealthCheckResponse.ServingStatus.SERVING);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(org.springframework.boot.health.contributor.Status.UP);
        }

        @Test
        @DisplayName("when health is checked - then the check the server received names the empty service")
        void whenHealthIsChecked_thenCheckTheServerReceivedNamesTheEmptyService() {
            GrpcStubServer.reportServingStatus(HealthCheckResponse.ServingStatus.SERVING);

            healthIndicator.health();

            assertThat(GrpcStubServer.lastHealthCheckRequest().getService()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(
                value = HealthCheckResponse.ServingStatus.class,
                names = {"NOT_SERVING", "UNKNOWN", "SERVICE_UNKNOWN"})
        @DisplayName("when the stub server reports a non-SERVING status - then the status is DOWN and the detail "
                + "names it")
        void whenStubServerReportsNonServingStatus_thenStatusIsDownAndDetailCarriesServingStatus(
                HealthCheckResponse.ServingStatus servingStatus) {
            GrpcStubServer.reportServingStatus(servingStatus);

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(org.springframework.boot.health.contributor.Status.DOWN);
            assertThat(health.getDetails()).containsValue(servingStatus.name());
        }

        @ParameterizedTest
        @EnumSource(
                value = Status.Code.class,
                names = {"UNAVAILABLE", "UNIMPLEMENTED"})
        @DisplayName(
                "when the stub server fails the health check - then the status is DOWN and the detail names the status")
        void whenStubServerFailsHealthCheck_thenStatusIsDownAndDetailNamesStatus(Status.Code code) {
            GrpcStubServer.failHealthCheckWith(Status.fromCode(code).withDescription("stub failure"));

            Health health = healthIndicator.health();

            assertThat(health.getStatus()).isEqualTo(org.springframework.boot.health.contributor.Status.DOWN);
            assertThat(health.getDetails()).containsValue(code.name());
        }
    }
}
