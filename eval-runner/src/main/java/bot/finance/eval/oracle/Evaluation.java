package bot.finance.eval.oracle;

import java.util.List;
import java.util.Map;

/**
 * What the oracle made of one attempt. Matching pairs are null for a count-only case; a metric is null where its
 * formula is undefined.
 */
public record Evaluation(
        List<Check> checks,
        int expectedCount,
        int actualCount,
        int matchedCount,
        Map<Integer, Long> matchedPairs,
        String verdict,
        Double precision,
        Double recall,
        Double f1) {}
