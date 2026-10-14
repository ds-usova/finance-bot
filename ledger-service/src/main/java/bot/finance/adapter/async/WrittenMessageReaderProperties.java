package bot.finance.adapter.async;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("written-message.reader")
public record WrittenMessageReaderProperties(Duration pollInterval, Duration sweepMargin, Duration maxQueueWait) {

    public WrittenMessageReaderProperties {
        if (maxQueueWait == null || maxQueueWait.isZero() || maxQueueWait.isNegative()) {
            throw new IllegalArgumentException("TURN_QUEUE_MAX_WAIT must be a positive duration");
        }
    }
}
