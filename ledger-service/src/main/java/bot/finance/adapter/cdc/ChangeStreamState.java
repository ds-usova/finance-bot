package bot.finance.adapter.cdc;

/**
 * What {@code /actuator/health} and {@code ledger_cdc_state} both read: whether this instance's engine holds
 * the slot and is consuming the log, is waiting behind another instance that does, or cannot stream at all.
 */
public enum ChangeStreamState {
    STREAMING,
    STANDBY,
    DOWN
}
