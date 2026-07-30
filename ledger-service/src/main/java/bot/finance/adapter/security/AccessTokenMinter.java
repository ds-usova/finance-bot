package bot.finance.adapter.security;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
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

    public String mint(String userExternalId) {
        // signs an RS256 JWT carrying sub, iss, aud, iat, exp at the configured ttl and a random jti
        return null;
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
