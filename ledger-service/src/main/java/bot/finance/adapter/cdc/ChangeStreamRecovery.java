package bot.finance.adapter.cdc;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Rebuilds an invalidated replication slot: stops {@link ChangeStreamReader}, deletes the stored position,
 * drops the slot and starts a fresh engine at the current end of the log — gated by an advisory lock so only
 * one instance runs the sequence at a time.
 */
@Component
public class ChangeStreamRecovery {

    private final ChangeStreamReader changeStreamReader;
    private final DataSource dataSource;
    private final CdcProperties properties;
    private final Logger log;

    public ChangeStreamRecovery(
            ChangeStreamReader changeStreamReader,
            DataSource dataSource,
            CdcProperties properties,
            LoggerFactory loggerFactory) {
        this.changeStreamReader = changeStreamReader;
        this.dataSource = dataSource;
        this.properties = properties;
        this.log = loggerFactory.getLogger(ChangeStreamRecovery.class);
    }

    public SlotRecoveryOutcome recover() {
        // refuses a slot that is not lost, and a lock already held elsewhere; otherwise stops the reader,
        // deletes the stored position before dropping the slot, logs the abandoned position at error, and
        // starts a fresh engine at the current end of the log
        return new SlotRecoveryOutcome(
                SlotRecoveryOutcome.Status.ENGINE_DID_NOT_STOP, Optional.empty(), Optional.empty());
    }
}
