package bot.finance.adapter.security;

import bot.finance.domain.value.MessageReference;
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
public class AccessTokenMinter {

    private final AccessTokenProperties properties;
    private final TokenSigningKeys signingKeys;

    public AccessTokenMinter(AccessTokenProperties properties, TokenSigningKeys signingKeys) {
        this.properties = properties;
        this.signingKeys = signingKeys;
    }

    public String mint(String userExternalId, MessageReference reference) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + properties.ttl().toMillis());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userExternalId)
                .issuer(properties.issuer())
                .audience(properties.audience())
                .issueTime(issuedAt)
                .expirationTime(expiresAt)
                .jwtID(UUID.randomUUID().toString())
                .claim("mrf", reference.value().toString())
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKeys.keyId())
                .build();
        SignedJWT signedJwt = new SignedJWT(header, claims);
        try {
            JWSSigner signer = new RSASSASigner(signingKeys.privateKey());
            signedJwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to sign MCP access token", e);
        }
        return signedJwt.serialize();
    }
}
