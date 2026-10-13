package bot.finance.eval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/** The run's aggregates, computed from its attempt records alone, as scoring.md defines them. */
final class Summary {

    private Summary() {}

    static Map<String, Object> of(List<Attempt> attempts, Map<String, String> blocked, long runMs) {
        List<Attempt> counted = attempts.stream()
                .filter(a -> a.attemptStatus.equals("completed"))
                .toList();
        List<Attempt> evaluated =
                counted.stream().filter(a -> !"error".equals(a.verdict)).toList();

        Map<String, List<Attempt>> byCase = new LinkedHashMap<>();
        counted.forEach(
                a -> byCase.computeIfAbsent(a.caseId, id -> new ArrayList<>()).add(a));
        Map<String, Object> cases = new LinkedHashMap<>();
        List<Double> casePrecisions = new ArrayList<>();
        List<Double> caseRecalls = new ArrayList<>();
        List<Double> caseF1s = new ArrayList<>();
        byCase.forEach((caseId, caseAttempts) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("verdicts", caseAttempts.stream().map(a -> a.verdict).toList());
            entry.put("verdictCounts", verdictCounts(caseAttempts));
            Double precision = mean(values(caseAttempts, a -> a.qualityMetrics.precision));
            Double recall = mean(values(caseAttempts, a -> a.qualityMetrics.recall));
            Double f1 = mean(values(caseAttempts, a -> a.qualityMetrics.f1));
            entry.put("precision", precision);
            entry.put("recall", recall);
            entry.put("f1", f1);
            entry.put("targetTurnMs", Stats.of(values(caseAttempts, a -> toDouble(a.measurements.targetTurnMs))));
            cases.put(caseId, entry);
            addIfPresent(casePrecisions, precision);
            addIfPresent(caseRecalls, recall);
            addIfPresent(caseF1s, f1);
        });

        Map<String, Object> quality = new LinkedHashMap<>();
        Map<String, Long> counts = verdictCounts(counted);
        long pass = counts.get("pass");
        long fail = counts.get("fail");
        long error = counts.get("error");
        quality.put("verdictCounts", counts);
        quality.put("passRate", pass + fail == 0 ? null : (double) pass / (pass + fail));
        quality.put("errorRate", counted.isEmpty() ? null : (double) error / counted.size());
        quality.put("precision", Stats.meanWithCount(casePrecisions));
        quality.put("recall", Stats.meanWithCount(caseRecalls));
        quality.put("f1", Stats.meanWithCount(caseF1s));
        List<Attempt> expectingNone = evaluated.stream()
                .filter(a -> a.matching != null && a.matching.expected == 0)
                .toList();
        quality.put(
                "falsePositiveRate",
                expectingNone.isEmpty()
                        ? null
                        : (double) expectingNone.stream()
                                        .filter(a -> a.matching.actual > 0)
                                        .count()
                                / expectingNone.size());
        Map<String, Map<String, Long>> confusion = new TreeMap<>();
        for (Attempt a : evaluated) {
            if (a.expected != null) {
                confusion
                        .computeIfAbsent(a.expected.path("messageOutcome").asString(), k -> new TreeMap<>())
                        .merge(String.valueOf(a.actual.outcome), 1L, Long::sum);
            }
        }
        quality.put("outcomeConfusion", confusion);
        Map<String, Object> byCovers = new TreeMap<>();
        Map<String, List<Attempt>> coversGroups = new TreeMap<>();
        evaluated.forEach(a ->
                coversGroups.computeIfAbsent(a.covers, k -> new ArrayList<>()).add(a));
        coversGroups.forEach((covers, group) -> byCovers.put(
                covers,
                (double) group.stream().filter(a -> "pass".equals(a.verdict)).count() / group.size()));
        quality.put("passRateByCovers", byCovers);

        Map<String, Object> performance = new LinkedHashMap<>();
        performance.put("runMs", runMs);
        performance.put("targetTurnMs", Stats.of(values(evaluated, a -> toDouble(a.measurements.targetTurnMs))));
        performance.put("providerChatCalls", Stats.of(values(evaluated, a -> a.measurements.providerChatCalls)));
        performance.put("providerChatSeconds", Stats.of(values(evaluated, a -> a.measurements.providerChatSeconds)));
        performance.put("inputTokens", Stats.of(values(evaluated, a -> a.measurements.inputTokens)));
        performance.put("outputTokens", Stats.of(values(evaluated, a -> a.measurements.outputTokens)));
        performance.put("ledgerToolCalls", Stats.of(values(evaluated, a -> a.measurements.ledgerToolCalls)));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("attempts", attempts.size());
        summary.put("discardedRolloverAttempts", attempts.size() - counted.size());
        summary.put("blockedCases", blocked);
        summary.put("quality", quality);
        summary.put("performance", performance);
        summary.put("cases", cases);
        return summary;
    }

    private static Map<String, Long> verdictCounts(Collection<Attempt> attempts) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String verdict : List.of("pass", "fail", "error")) {
            counts.put(
                    verdict,
                    attempts.stream().filter(a -> verdict.equals(a.verdict)).count());
        }
        return counts;
    }

    private static List<Double> values(List<Attempt> attempts, Function<Attempt, Double> value) {
        return attempts.stream()
                .filter(a -> !"error".equals(a.verdict))
                .map(value)
                .filter(Objects::nonNull)
                .toList();
    }

    private static Double mean(List<Double> values) {
        return values.isEmpty()
                ? null
                : values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private static Double toDouble(Long value) {
        return value == null ? null : value.doubleValue();
    }

    private static void addIfPresent(List<Double> into, Double value) {
        if (value != null) {
            into.add(value);
        }
    }
}
