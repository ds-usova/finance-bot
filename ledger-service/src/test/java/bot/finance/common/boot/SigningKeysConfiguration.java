package bot.finance.common.boot;

import bot.finance.adapter.security.TokenSigningKeys;
import bot.finance.common.fixtures.SigningKeys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** The key pair both decoders verify against, which a framework slice does not component-scan. */
@TestConfiguration(proxyBeanMethods = false)
public class SigningKeysConfiguration {

    @Bean
    TokenSigningKeys tokenSigningKeys() {
        return SigningKeys.keys();
    }
}
