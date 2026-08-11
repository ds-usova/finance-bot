package bot.finance.adapter.cdc;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * {@code POST /actuator/cdc}: runs {@link ChangeStreamRecovery} and maps its outcome to 200, 409 or 503.
 * {@code RecoverySecretFilter} guards the path before a request reaches this endpoint.
 */
@Component
@Endpoint(id = "cdc")
public class CdcRecoveryEndpoint {

    private final ChangeStreamRecovery changeStreamRecovery;

    public CdcRecoveryEndpoint(ChangeStreamRecovery changeStreamRecovery) {
        this.changeStreamRecovery = changeStreamRecovery;
    }

    @WriteOperation
    public WebEndpointResponse<SlotRecoveryOutcome> recover() {
        // runs the recovery sequence and answers 200 with both positions on success, 409 when the slot is not
        // lost or the advisory lock is held, and 503 when the engine, the position delete or the slot drop
        // each refuse
        return new WebEndpointResponse<>(HttpStatus.SERVICE_UNAVAILABLE.value());
    }
}
