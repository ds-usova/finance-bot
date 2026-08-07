package bot.finance.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.common.fixtures.SigningKeys;
import java.security.KeyStore;
import java.security.interfaces.RSAPublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

class TokenSigningKeysTest {

    @Nested
    @DisplayName("loading the key pair")
    class Load {

        @Test
        @DisplayName("when the configured keystore is read - then publicKey() is the key pair the alias resolves to")
        void whenTheConfiguredKeystoreIsRead_thenPublicKeyIsTheKeyPairTheAliasResolvesTo() throws Exception {
            TokenSigningKeys keys = SigningKeys.keys();

            assertThat(keys.publicKey()).isEqualTo(publicKeyFromKeystore());
        }

        @Test
        @DisplayName("when the key pair is loaded - then privateKey() and publicKey() are the two halves of one pair")
        void whenTheKeyPairIsLoaded_thenPrivateKeyAndPublicKeyAreTheTwoHalvesOfOnePair() {
            TokenSigningKeys keys = SigningKeys.keys();

            assertThat(keys.privateKey().getAlgorithm()).isEqualTo("RSA");
            assertThat(keys.publicKey().getAlgorithm()).isEqualTo("RSA");
        }

        @Test
        @DisplayName("when the key pair is loaded - then keyId() is the configured key alias")
        void whenTheKeyPairIsLoaded_thenKeyIdIsTheConfiguredKeyAlias() {
            TokenSigningKeys keys = SigningKeys.keys();

            assertThat(keys.keyId()).isEqualTo(SigningKeys.KEY_ALIAS);
        }

        private static RSAPublicKey publicKeyFromKeystore() throws Exception {
            char[] password = SigningKeys.KEYSTORE_PASSWORD.toCharArray();
            Resource resource = new DefaultResourceLoader().getResource(SigningKeys.KEYSTORE);
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (var in = resource.getInputStream()) {
                keyStore.load(in, password);
            }
            return (RSAPublicKey) keyStore.getCertificate(SigningKeys.KEY_ALIAS).getPublicKey();
        }
    }

    @Nested
    @DisplayName("rejecting an unusable keystore")
    class Reject {

        @Test
        @DisplayName("when the keystore cannot be found - then construction fails rather than yielding no key")
        void whenTheKeystoreCannotBeFound_thenConstructionFailsRatherThanYieldingNoKey() {
            TokenSigningProperties missing = new TokenSigningProperties(
                    "classpath:no-such-keystore.p12", SigningKeys.KEYSTORE_PASSWORD, SigningKeys.KEY_ALIAS);

            assertThatThrownBy(() -> new TokenSigningKeys(missing, new DefaultResourceLoader()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("when the keystore password is wrong - then construction fails rather than yielding no key")
        void whenTheKeystorePasswordIsWrong_thenConstructionFailsRatherThanYieldingNoKey() {
            TokenSigningProperties wrongPassword =
                    new TokenSigningProperties(SigningKeys.KEYSTORE, "not-the-password", SigningKeys.KEY_ALIAS);

            assertThatThrownBy(() -> new TokenSigningKeys(wrongPassword, new DefaultResourceLoader()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
