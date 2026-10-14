package bot.finance.adapter.async;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("written-message.reader")
public record WrittenMessageReaderProperties(Duration pollInterval, Duration sweepMargin, Duration maxQueueWait) {

    public WrittenMessageReaderProperties {
        // TODO: refuse a null, zero or negative longest queue wait, naming TURN_QUEUE_MAX_WAIT
    }
}
