package bot.finance.adapter.aiconnector;

import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class AiConnectorHealthIndicator implements HealthIndicator {

    private final HealthGrpc.HealthBlockingStub healthStub;

    public AiConnectorHealthIndicator(HealthGrpc.HealthBlockingStub healthStub) {
        this.healthStub = healthStub;
    }

    @Override
    public Health health() {
        try {
            HealthCheckResponse response = healthStub.check(
                    HealthCheckRequest.newBuilder().setService("").build());
            HealthCheckResponse.ServingStatus servingStatus = response.getStatus();
            Health.Builder builder =
                    servingStatus == HealthCheckResponse.ServingStatus.SERVING ? Health.up() : Health.down();
            return builder.withDetail("status", servingStatus.name()).build();
        } catch (StatusRuntimeException e) {
            return Health.down()
                    .withDetail("status", e.getStatus().getCode().name())
                    .build();
        }
    }
}
