package bot.finance.adapter.security;

import bot.finance.domain.value.MessageReference;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenMinter {

    private final AccessTokenProperties properties;
    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public AccessTokenMinter(AccessTokenProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        KeyStore.PrivateKeyEntry entry = loadPrivateKeyEntry(properties, resourceLoader);
        this.privateKey = entry.getPrivateKey();
        this.publicKey = (RSAPublicKey) entry.getCertificate().getPublicKey();
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
        JWSHeader header =
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId()).build();
        SignedJWT signedJwt = new SignedJWT(header, claims);
        try {
            JWSSigner signer = new RSASSASigner(privateKey);
            signedJwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("failed to sign MCP access token", e);
        }
        return signedJwt.serialize();
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return properties.keyAlias();
    }

    private static KeyStore.PrivateKeyEntry loadPrivateKeyEntry(
            AccessTokenProperties properties, ResourceLoader resourceLoader) {
        char[] password = properties.keystorePassword().toCharArray();
        try {
            Resource resource = resourceLoader.getResource(properties.keystore());
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream in = resource.getInputStream()) {
                keyStore.load(in, password);
            }
            KeyStore.ProtectionParameter protection = new KeyStore.PasswordProtection(password);
            return (KeyStore.PrivateKeyEntry) keyStore.getEntry(properties.keyAlias(), protection);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("failed to load MCP signing keystore " + properties.keystore(), e);
        }
    }
}
