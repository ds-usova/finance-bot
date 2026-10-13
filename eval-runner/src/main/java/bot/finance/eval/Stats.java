package bot.finance.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sample statistics; durations are reported as median and p90, never as the mean alone. */
final class Stats {

    private Stats() {}

    static Map<String, Object> of(List<Double> values) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("n", values.size());
        stats.put("median", percentile(values, 50));
        stats.put("p90", percentile(values, 90));
        stats.put(
                "mean",
                values.isEmpty()
                        ? null
                        : values.stream()
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElseThrow());
        return stats;
    }

    static Map<String, Object> meanWithCount(List<Double> values) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("n", values.size());
        stats.put(
                "mean",
                values.isEmpty()
                        ? null
                        : values.stream()
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElseThrow());
        return stats;
    }

    /** Nearest-rank percentile, or null for no values. */
    static Double percentile(List<Double> values, int percent) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compare);
        int rank = (int) Math.ceil(percent / 100.0 * sorted.size());
        return sorted.get(Math.max(rank, 1) - 1);
    }
}
