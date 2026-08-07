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

/** The one key pair every token this service issues is signed with, whatever its audience. */
@Component
public class TokenSigningKeys {

    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;

    public TokenSigningKeys(TokenSigningProperties properties, ResourceLoader resourceLoader) {
        KeyStore.PrivateKeyEntry entry = loadPrivateKeyEntry(properties, resourceLoader);

        this.privateKey = entry.getPrivateKey();
        this.publicKey = (RSAPublicKey) entry.getCertificate().getPublicKey();
        this.keyId = properties.keyAlias();
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    private static KeyStore.PrivateKeyEntry loadPrivateKeyEntry(
            TokenSigningProperties properties, ResourceLoader resourceLoader) {
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
            throw new IllegalStateException("failed to load token signing keystore " + properties.keystore(), e);
        }
    }
}
