package bot.finance.common.fixtures;

import bot.finance.adapter.security.TokenSigningKeys;
import bot.finance.adapter.security.TokenSigningProperties;
import org.springframework.core.io.DefaultResourceLoader;

/** The {@code token.signing.*} configuration the test profile runs with, and the key pair it resolves to. */
public final class SigningKeys {

    public static final String KEYSTORE = "classpath:local-mcp-signing.p12";
    public static final String KEYSTORE_PASSWORD = "changeit";
    public static final String KEY_ALIAS = "mcp-signing";

    private SigningKeys() {}

    public static TokenSigningProperties properties() {
        return new TokenSigningProperties(KEYSTORE, KEYSTORE_PASSWORD, KEY_ALIAS);
    }

    public static TokenSigningKeys keys() {
        return new TokenSigningKeys(properties(), new DefaultResourceLoader());
    }
}
