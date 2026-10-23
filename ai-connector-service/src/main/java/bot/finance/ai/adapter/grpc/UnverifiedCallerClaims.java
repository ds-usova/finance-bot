package bot.finance.ai.adapter.grpc;

import bot.finance.ai.domain.exception.CallerNotIdentifiedException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.MessageIdentity;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

public record UnverifiedCallerClaims(MessageIdentity identity, Optional<Instant> expiry) {

    private static final String BEARER_PREFIX = "Bearer ";

    public static UnverifiedCallerClaims read(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new CallerNotIdentifiedException("Authorization header is not a Bearer token");
        }
        try {
            JWTClaimsSet claims = JWTParser.parse(authorization.substring(BEARER_PREFIX.length()))
                    .getJWTClaimsSet();
            MessageIdentity identity = MessageIdentity.of(claims.getSubject(), claims.getStringClaim("imi"));
            Date expiry = claims.getExpirationTime();
            return new UnverifiedCallerClaims(
                    identity, Optional.ofNullable(expiry).map(Date::toInstant));
        } catch (ParseException e) {
            throw new CallerNotIdentifiedException("Caller token could not be parsed", e);
        } catch (InvalidValueException e) {
            throw new CallerNotIdentifiedException("Caller token claims did not yield an identity", e);
        }
    }
}
