package bot.finance.adapter.metrics;

/** The names this adapter's meters are registered under, as they appear in the scrape. */
public enum MeterName {
    TOOL_CALLS("ledger_mcp_tool_calls_total"),
    TURNS("ledger_turns_total"),
    PROPOSALS_RESOLVED("ledger_proposals_resolved_total"),
    OUTBOX_FACTS_DROPPED("ledger_cdc_facts_dropped_total"),
    TURNS_SWEPT("ledger_turns_swept_total"),
    TURN_QUEUE_OLDEST("ledger_turn_queue_oldest_seconds");

    private final String meterName;

    MeterName(String meterName) {
        this.meterName = meterName;
    }

    public String meterName() {
        return meterName;
    }
}
