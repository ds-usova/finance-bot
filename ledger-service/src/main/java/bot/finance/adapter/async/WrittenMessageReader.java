package bot.finance.adapter.async;

import bot.finance.application.port.HandleWrittenMessagePort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SweepWrittenMessagesPort;
import bot.finance.application.port.WrittenMessageReadingDispatchPort;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class WrittenMessageReader implements SmartLifecycle, WrittenMessageReadingDispatchPort {

    private final ScheduledExecutorService writtenMessageReaderExecutor;
    private final HandleWrittenMessagePort handleWrittenMessagePort;
    private final SweepWrittenMessagesPort sweepWrittenMessagesPort;
    private final WrittenMessageReaderProperties properties;
    private final Logger log;

    public WrittenMessageReader(
            ScheduledExecutorService writtenMessageReaderExecutor,
            HandleWrittenMessagePort handleWrittenMessagePort,
            SweepWrittenMessagesPort sweepWrittenMessagesPort,
            WrittenMessageReaderProperties properties,
            LoggerFactory loggerFactory) {
        this.writtenMessageReaderExecutor = writtenMessageReaderExecutor;
        this.handleWrittenMessagePort = handleWrittenMessagePort;
        this.sweepWrittenMessagesPort = sweepWrittenMessagesPort;
        this.properties = properties;
        this.log = loggerFactory.getLogger(WrittenMessageReader.class);
    }

    @Override
    public void start() {
        // stub-intent: schedule one pass every poll interval from start, each pass sweeping and then reading
        // until nothing waits, every failure logged and swallowed.
    }

    @Override
    public void stop() {
        // stub-intent: cancel the scheduled passes and mark the reader as not running.
    }

    @Override
    public boolean isRunning() {
        // stub-intent: true from start until stop.
        return false;
    }

    @Override
    public void dispatch() {
        // stub-intent: submit one more pass now, sweeping and then reading until nothing waits, every failure
        // logged and swallowed so the caller never sees one.
    }
}
