package bot.finance.adapter.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mcp.token")
public record AccessTokenProperties(String issuer, String audience, Duration ttl) {}
