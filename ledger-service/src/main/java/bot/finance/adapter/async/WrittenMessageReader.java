package bot.finance.adapter.async;

import bot.finance.application.port.HandleWrittenMessagePort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SweepWrittenMessagesPort;
import bot.finance.application.port.WrittenMessageReadingDispatchPort;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class WrittenMessageReader implements SmartLifecycle, WrittenMessageReadingDispatchPort {

    private final ScheduledExecutorService writtenMessageReaderExecutor;
    private final HandleWrittenMessagePort handleWrittenMessagePort;
    private final SweepWrittenMessagesPort sweepWrittenMessagesPort;
    private final WrittenMessageReaderProperties properties;
    private final Logger log;
    private volatile ScheduledFuture<?> scheduledPass;

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
        scheduledPass = writtenMessageReaderExecutor.scheduleWithFixedDelay(
                this::pass, 0, properties.pollInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        ScheduledFuture<?> pass = scheduledPass;
        scheduledPass = null;
        if (pass != null) {
            pass.cancel(false);
        }
    }

    @Override
    public boolean isRunning() {
        return scheduledPass != null;
    }

    @Override
    public void dispatch() {
        try {
            writtenMessageReaderExecutor.execute(this::pass);
        } catch (RejectedExecutionException e) {
            log.warn("written message reading pass was refused", e);
        }
    }

    private void pass() {
        sweep();
        readUntilNothingWaits();
    }

    private void sweep() {
        try {
            sweepWrittenMessagesPort.sweep();
        } catch (RuntimeException e) {
            log.error("failed to sweep written messages", e);
        }
    }

    private void readUntilNothingWaits() {
        try {
            while (handleWrittenMessagePort.handleNext()) {
                // keep reading while a text was claimed
            }
        } catch (RuntimeException e) {
            log.error("failed to read written messages", e);
        }
    }
}
