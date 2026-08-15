package bot.finance.ai.adapter.security;

import bot.finance.ai.domain.exception.CallerNotIdentifiedException;
import bot.finance.ai.domain.exception.CallerVerificationUnavailableException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.MessageIdentity;
import com.nimbusds.jose.RemoteKeySourceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class CallerTokenVerifier {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    public CallerTokenVerifier(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    public MessageIdentity verify(String authorization) {
        String token = stripBearerScheme(authorization);
        Jwt jwt = decode(token);

        try {
            return MessageIdentity.of(jwt.getSubject(), jwt.getClaimAsString("imi"));
        } catch (InvalidValueException e) {
            throw new CallerNotIdentifiedException("Caller token claims did not yield an identity", e);
        }
    }

    private static String stripBearerScheme(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new CallerNotIdentifiedException("Authorization header is not a Bearer token");
        }
        return authorization.substring(BEARER_PREFIX.length());
    }

    private Jwt decode(String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException e) {
            if (isKeySetUnavailable(e)) {
                throw new CallerVerificationUnavailableException("Caller token key set could not be fetched", e);
            }
            throw new CallerNotIdentifiedException("Caller token failed verification", e);
        }
    }

    private static boolean isKeySetUnavailable(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof RemoteKeySourceException) {
                return true;
            }
        }
        return false;
    }
}
