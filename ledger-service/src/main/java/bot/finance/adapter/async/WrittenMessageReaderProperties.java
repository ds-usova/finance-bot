package bot.finance.adapter.async;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("written-message.reader")
public record WrittenMessageReaderProperties(Duration pollInterval, Duration sweepMargin) {}
