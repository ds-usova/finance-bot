package bot.finance.adapter.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("session.token")
public record SessionTokenProperties(String issuer, String audience, Duration ttl) {}
