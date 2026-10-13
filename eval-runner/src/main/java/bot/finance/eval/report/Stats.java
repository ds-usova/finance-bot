package bot.finance.eval.report;

import bot.finance.eval.Attempt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Sample statistics over attempts; durations are reported as median and p90, never as the mean alone. */
final class Stats {

    private Stats() {}

    static Map<String, Object> of(List<Double> values) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("n", values.size());
        stats.put("median", percentile(values, 50));
        stats.put("p90", percentile(values, 90));
        stats.put("mean", mean(values));
        return stats;
    }

    static Map<String, Object> meanWithCount(List<Double> values) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("n", values.size());
        stats.put("mean", mean(values));
        return stats;
    }

    static Double mean(List<Double> values) {
        return values.isEmpty()
                ? null
                : values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
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

    /** The values of the attempts that are not errors, where present; an error's value is never a sample. */
    static List<Double> values(Collection<Attempt> attempts, Function<Attempt, ? extends Number> value) {
        return attempts.stream()
                .filter(a -> !"error".equals(a.verdict))
                .map(value)
                .filter(Objects::nonNull)
                .map(Number::doubleValue)
                .toList();
    }

    static Map<String, Long> verdictCounts(Collection<Attempt> attempts) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String verdict : List.of("pass", "fail", "error")) {
            counts.put(
                    verdict,
                    attempts.stream().filter(a -> verdict.equals(a.verdict)).count());
        }
        return counts;
    }
}
