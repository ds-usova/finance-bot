package bot.finance.ai.common.fixtures;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * A JVM-wide RSA key pair standing in for the ledger's own, plus a second one the published key set never
 * carries. Mints tokens matching {@code CallerTokenProperties}' test-profile issuer and audience, in the
 * {@code Bearer} form a request's {@code authorization} header carries.
 */
public final class CallerTokens {

    public static final String ISSUER = "ledger-service";
    public static final String AUDIENCE = "mcp-adapter";

    private static final String INCOMING_MESSAGE_ID_CLAIM = "imi";
    private static final Duration TTL = Duration.ofMinutes(2);

    private static final RSAKey SIGNING_KEY = generate("caller-token-test-key");
    private static final RSAKey UNPUBLISHED_KEY = generate("caller-token-unpublished-key");

    private CallerTokens() {}

    /** The published key set — {@link #SIGNING_KEY}'s public half only — as the JWKS endpoint serves it. */
    public static String publicJwkSetJson() {
        return new JWKSet(SIGNING_KEY.toPublicJWK()).toString();
    }

    public static String bearer(long userId, String incomingMessageId) {
        Instant issuedAt = Instant.now();
        return bearerToken(
                SIGNING_KEY, Long.toString(userId), ISSUER, AUDIENCE, issuedAt, issuedAt.plus(TTL), incomingMessageId);
    }

    public static String bearerSignedByUnpublishedKey(long userId, String incomingMessageId) {
        Instant issuedAt = Instant.now();
        return bearerToken(
                UNPUBLISHED_KEY,
                Long.toString(userId),
                ISSUER,
                AUDIENCE,
                issuedAt,
                issuedAt.plus(TTL),
                incomingMessageId);
    }

    public static String bearerExpired(long userId, String incomingMessageId) {
        Instant issuedAt = Instant.now().minus(TTL).minusSeconds(60);
        return bearerToken(
                SIGNING_KEY, Long.toString(userId), ISSUER, AUDIENCE, issuedAt, issuedAt.plus(TTL), incomingMessageId);
    }

    public static String bearerWrongIssuer(long userId, String incomingMessageId) {
        Instant issuedAt = Instant.now();
        return bearerToken(
                SIGNING_KEY,
                Long.toString(userId),
                "some-other-issuer",
                AUDIENCE,
                issuedAt,
                issuedAt.plus(TTL),
                incomingMessageId);
    }

    public static String bearerWrongAudience(long userId, String incomingMessageId) {
        Instant issuedAt = Instant.now();
        return bearerToken(
                SIGNING_KEY,
                Long.toString(userId),
                ISSUER,
                "some-other-audience",
                issuedAt,
                issuedAt.plus(TTL),
                incomingMessageId);
    }

    public static String bearerNoSubject(String incomingMessageId) {
        Instant issuedAt = Instant.now();
        return bearerToken(SIGNING_KEY, null, ISSUER, AUDIENCE, issuedAt, issuedAt.plus(TTL), incomingMessageId);
    }

    public static String bearerNonNumericSubject(String incomingMessageId) {
        Instant issuedAt = Instant.now();
        return bearerToken(
                SIGNING_KEY, "not-a-number", ISSUER, AUDIENCE, issuedAt, issuedAt.plus(TTL), incomingMessageId);
    }

    public static String bearerNoIncomingMessageId(long userId) {
        Instant issuedAt = Instant.now();
        return bearerToken(SIGNING_KEY, Long.toString(userId), ISSUER, AUDIENCE, issuedAt, issuedAt.plus(TTL), null);
    }

    public static String bearerBlankIncomingMessageId(long userId) {
        Instant issuedAt = Instant.now();
        return bearerToken(SIGNING_KEY, Long.toString(userId), ISSUER, AUDIENCE, issuedAt, issuedAt.plus(TTL), "   ");
    }

    private static String bearerToken(
            RSAKey key,
            String subject,
            String issuer,
            String audience,
            Instant issuedAt,
            Instant expiresAt,
            String incomingMessageId) {
        return "Bearer " + sign(key, subject, issuer, audience, issuedAt, expiresAt, incomingMessageId);
    }

    private static String sign(
            RSAKey key,
            String subject,
            String issuer,
            String audience,
            Instant issuedAt,
            Instant expiresAt,
            String incomingMessageId) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(UUID.randomUUID().toString());
            if (subject != null) {
                claims.subject(subject);
            }
            if (incomingMessageId != null) {
                claims.claim(INCOMING_MESSAGE_ID_CLAIM, incomingMessageId);
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(key.getKeyID())
                            .build(),
                    claims.build());
            jwt.sign(new RSASSASigner(key.toRSAPrivateKey()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to sign test caller token", e);
        }
    }

    private static RSAKey generate(String keyId) {
        try {
            return new RSAKeyGenerator(2048)
                    .keyID(keyId)
                    .keyUse(KeyUse.SIGNATURE)
                    .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to generate test signing key", e);
        }
    }
}
