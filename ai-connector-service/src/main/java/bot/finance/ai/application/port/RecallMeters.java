package bot.finance.ai.application.port;

/**
 * Records the memory's recall outcomes — the number of examples a query returned, and the closest neighbour's
 * similarity score.
 */
public interface RecallMeters {

    void recordExamples(int returned);

    void recordBestSimilarity(double score);
}
