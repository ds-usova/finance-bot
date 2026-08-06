package bot.finance.adapter.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("token.signing")
public record TokenSigningProperties(String keystore, String keystorePassword, String keyAlias) {}
