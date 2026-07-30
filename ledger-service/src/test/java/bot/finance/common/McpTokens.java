package bot.finance.common;

import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.adapter.security.AccessTokenProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Mints valid tokens through the application's own {@link AccessTokenMinter}, and hand-signs malformed ones from
 * the same keystore that neither the minter nor a well-behaved caller would ever produce.
 */
public final class McpTokens {

    private static final String KEYSTORE = "classpath:local-mcp-signing.p12";
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final String KEY_ALIAS = "mcp-signing";
    private static final String ISSUER = "ledger-service";
    private static final String AUDIENCE = "mcp-adapter";
    private static final Duration TTL = Duration.ofMinutes(2);

    private McpTokens() {}

    /** The {@code mcp.token.*} configuration the test profile runs with, for a hand-built {@link AccessTokenMinter}. */
    public static AccessTokenProperties properties() {
        return new AccessTokenProperties(KEYSTORE, KEYSTORE_PASSWORD, KEY_ALIAS, ISSUER, AUDIENCE, TTL);
    }

    public static String tokenFor(AccessTokenMinter accessTokenMinter, String externalId) {
        return accessTokenMinter.mint(externalId);
    }

    public static String expiredToken(String externalId) {
        Instant issuedAt = Instant.now().minus(TTL).minusSeconds(60);
        return sign(externalId, AUDIENCE, issuedAt, issuedAt.plus(TTL));
    }

    public static String wrongAudienceToken(String externalId) {
        Instant issuedAt = Instant.now();
        return sign(externalId, "some-other-audience", issuedAt, issuedAt.plus(TTL));
    }

    public static String overTtlToken(String externalId) {
        Instant issuedAt = Instant.now();
        return sign(externalId, AUDIENCE, issuedAt, issuedAt.plus(TTL).plus(Duration.ofMinutes(10)));
    }

    private static String sign(String subject, String audience, Instant issuedAt, Instant expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(ISSUER)
                    .audience(audience)
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(UUID.randomUUID().toString())
                    .build();
            SignedJWT jwt =
                    new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ALIAS).build(), claims);
            jwt.sign(new RSASSASigner(loadPrivateKey()));
            return jwt.serialize();
        } catch (GeneralSecurityException | IOException | JOSEException e) {
            throw new IllegalStateException("failed to sign test MCP token", e);
        }
    }

    private static PrivateKey loadPrivateKey() throws GeneralSecurityException, IOException {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource(KEYSTORE);
        char[] password = KEYSTORE_PASSWORD.toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = resource.getInputStream()) {
            keyStore.load(in, password);
        }
        return (PrivateKey) keyStore.getKey(KEY_ALIAS, password);
    }
}
