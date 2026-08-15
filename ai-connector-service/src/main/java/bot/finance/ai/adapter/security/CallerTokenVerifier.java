package bot.finance.ai.adapter.security;

import bot.finance.ai.domain.value.MessageIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class CallerTokenVerifier {

    private final JwtDecoder jwtDecoder;

    public CallerTokenVerifier(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    public MessageIdentity verify(String authorization) {
        // strips the Bearer scheme, decodes and validates the token, and reads sub and imi into a MessageIdentity;
        // a token that does not verify or lacks either claim is CallerNotIdentifiedException, a key set that
        // cannot be fetched is CallerVerificationUnavailableException
        return null;
    }
}
