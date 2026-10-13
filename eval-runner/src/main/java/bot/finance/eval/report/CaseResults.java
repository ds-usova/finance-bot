package bot.finance.eval.report;

import bot.finance.eval.Attempt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Each case's repetitions side by side, with the means of its valid quality metrics. The run's quality means are
 * the means of these case means, so each case weighs the same.
 */
record CaseResults(Map<String, Object> byCase, List<Double> precisions, List<Double> recalls, List<Double> f1s) {

    static CaseResults of(List<Attempt> counted) {
        Map<String, List<Attempt>> grouped = new LinkedHashMap<>();
        counted.forEach(
                a -> grouped.computeIfAbsent(a.caseId, id -> new ArrayList<>()).add(a));

        CaseResults results =
                new CaseResults(new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        grouped.forEach(results::add);
        return results;
    }

    private void add(String caseId, List<Attempt> attempts) {
        Double precision = Stats.mean(Stats.values(attempts, a -> a.qualityMetrics.precision));
        Double recall = Stats.mean(Stats.values(attempts, a -> a.qualityMetrics.recall));
        Double f1 = Stats.mean(Stats.values(attempts, a -> a.qualityMetrics.f1));

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("verdicts", attempts.stream().map(a -> a.verdict).toList());
        entry.put("verdictCounts", Stats.verdictCounts(attempts));
        entry.put("precision", precision);
        entry.put("recall", recall);
        entry.put("f1", f1);
        entry.put("targetTurnMs", Stats.of(Stats.values(attempts, a -> a.measurements.targetTurnMs)));
        byCase.put(caseId, entry);

        addIfPresent(precisions, precision);
        addIfPresent(recalls, recall);
        addIfPresent(f1s, f1);
    }

    private static void addIfPresent(List<Double> into, Double value) {
        if (value != null) {
            into.add(value);
        }
    }
}
