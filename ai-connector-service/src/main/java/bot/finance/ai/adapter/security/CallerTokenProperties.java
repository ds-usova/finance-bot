package bot.finance.ai.adapter.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ledger.token")
public record CallerTokenProperties(String issuer, String audience, String jwksUri, Duration jwksTimeout) {}
