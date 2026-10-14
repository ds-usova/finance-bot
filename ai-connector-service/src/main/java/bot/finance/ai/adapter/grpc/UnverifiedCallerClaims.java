package bot.finance.ai.adapter.grpc;

import bot.finance.ai.domain.value.MessageIdentity;
import java.time.Instant;
import java.util.Optional;

public record UnverifiedCallerClaims(MessageIdentity identity, Optional<Instant> expiry) {

    public static UnverifiedCallerClaims read(String authorization) {
        // stub-intent: parses a Bearer JWT without verifying its signature or its times, and reads the person from
        // sub, the message from imi and the expiry from exp when present; a value that is no Bearer JWT, or whose
        // sub or imi do not yield a MessageIdentity, throws CallerNotIdentifiedException
        return null;
    }
}
