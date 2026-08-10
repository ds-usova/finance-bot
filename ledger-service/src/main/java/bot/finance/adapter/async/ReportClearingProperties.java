package bot.finance.adapter.async;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("report-clearing.pool")
public record ReportClearingProperties(int coreSize, int maxSize, int queueCapacity) {}
