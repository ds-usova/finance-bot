package bot.finance.ai.adapter.metrics;

/** The names this module's own meters are registered under, as they appear in the scrape. */
public enum MeterName {
    RECALL_EXAMPLES("ai_recall_examples"),
    RECALL_BEST_SIMILARITY("ai_recall_best_similarity"),
    CDC_ENTRIES_PENDING("ai_cdc_entries_pending"),
    CDC_DELIVERIES_DROPPED("ai_cdc_deliveries_dropped_total");

    private final String meterName;

    MeterName(String meterName) {
        this.meterName = meterName;
    }

    public String meterName() {
        return meterName;
    }
}
