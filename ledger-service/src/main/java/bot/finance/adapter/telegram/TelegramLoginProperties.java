package bot.finance.adapter.telegram;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("telegram.login")
public record TelegramLoginProperties(Duration maxAge) {}
