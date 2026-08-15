package bot.finance.adapter.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SessionTokenMinter {

    private final SessionTokenProperties properties;
    private final TokenSigningKeys signingKeys;

    public SessionTokenMinter(SessionTokenProperties properties, TokenSigningKeys signingKeys) {
        this.properties = properties;
        this.signingKeys = signingKeys;
    }

    public String mint(long userId) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + properties.ttl().toMillis());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(Long.toString(userId))
                .issuer(properties.issuer())
                .audience(properties.audience())
                .issueTime(issuedAt)
                .expirationTime(expiresAt)
                .jwtID(UUID.randomUUID().toString())
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKeys.keyId())
                .build();
        SignedJWT signedJwt = new SignedJWT(header, claims);
        try {
            JWSSigner signer = new RSASSASigner(signingKeys.privateKey());
            signedJwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to sign browser session token", e);
        }

        return signedJwt.serialize();
    }
}
