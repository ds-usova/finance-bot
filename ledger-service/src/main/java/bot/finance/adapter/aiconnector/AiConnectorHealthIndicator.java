package bot.finance.adapter.aiconnector;

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
        // TODO: GI02 calls the connector's grpc.health.v1.Health/Check for the empty service name -- the
        // server as a whole, which is the only name the connector registers -- and reports UP for SERVING
        // and DOWN for everything else, carrying the returned status as a detail.
        return Health.unknown().build();
    }
}
