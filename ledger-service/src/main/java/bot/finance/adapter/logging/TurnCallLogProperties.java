package bot.finance.adapter.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("turn-call-log")
public record TurnCallLogProperties(boolean enabled) {}
