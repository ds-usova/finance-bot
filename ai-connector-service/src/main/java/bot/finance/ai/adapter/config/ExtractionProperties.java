package bot.finance.ai.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("extraction")
public record ExtractionProperties(String mode) {}
