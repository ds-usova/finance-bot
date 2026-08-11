package bot.finance.adapter.cdc;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import java.time.Duration;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Owns the embedded Debezium engine's lifecycle: builds it against {@link CdcProperties}, runs it on the
 * shared {@link Executor}, offers each captured event to {@link ChangeEventPublisher}, and commits the log
 * position only once an event is published.
 */
@Component
public class ChangeStreamReader {

    private final CdcProperties properties;
    private final DataSource dataSource;
    private final ChangeEventPublisher publisher;
    private final ChangeStreamMeters meters;
    private final Executor changeStreamExecutor;
    private final Logger log;

    public ChangeStreamReader(
            CdcProperties properties,
            DataSource dataSource,
            ChangeEventPublisher publisher,
            ChangeStreamMeters meters,
            Executor changeStreamExecutor,
            LoggerFactory loggerFactory) {
        this.properties = properties;
        this.dataSource = dataSource;
        this.publisher = publisher;
        this.meters = meters;
        this.changeStreamExecutor = changeStreamExecutor;
        this.log = loggerFactory.getLogger(ChangeStreamReader.class);
    }

    public void start() {
        // builds the embedded engine and hands it to the executor; a held slot reports STANDBY and retries,
        // a log that cannot be read at all or a slot invalidated mid-stream reports DOWN and stops retrying
    }

    public boolean stop(Duration timeout) {
        // closes the engine and waits up to timeout for its task to finish, leaving the slot in place;
        // answers whether the task finished in time
        return false;
    }

    public ChangeStreamState state() {
        // answers the state the health indicator and the state gauge both read
        return ChangeStreamState.DOWN;
    }
}
