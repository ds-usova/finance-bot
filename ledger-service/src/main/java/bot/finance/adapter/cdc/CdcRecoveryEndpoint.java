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
        SlotRecoveryOutcome outcome = changeStreamRecovery.recover();

        return new WebEndpointResponse<>(outcome, statusFor(outcome).value());
    }

    private HttpStatus statusFor(SlotRecoveryOutcome outcome) {
        return switch (outcome.status()) {
            case REBUILT -> HttpStatus.OK;
            case SLOT_NOT_LOST, LOCK_HELD -> HttpStatus.CONFLICT;
            case ENGINE_DID_NOT_STOP, POSITION_NOT_DELETED, SLOT_NOT_DROPPED -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
