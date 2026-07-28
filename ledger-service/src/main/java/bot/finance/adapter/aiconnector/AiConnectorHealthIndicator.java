package bot.finance.adapter.aiconnector;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
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
    private final Logger log;

    public AiConnectorHealthIndicator(
            HealthGrpc.HealthBlockingStub healthStub,
            LoggerFactory loggerFactory
    ) {
        this.healthStub = healthStub;
        this.log = loggerFactory.getLogger(AiConnectorHealthIndicator.class);
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
            log.error("Failed to check the health of the AI connector", e);
            return Health.down()
                    .withDetail("status", e.getStatus().getCode().name())
                    .build();
        }
    }
}
