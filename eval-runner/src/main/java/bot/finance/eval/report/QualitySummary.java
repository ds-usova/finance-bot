package bot.finance.eval.report;

import bot.finance.eval.Attempt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The run's verdict rates, quality means, false positives and outcome confusion, as scoring.md defines them. */
final class QualitySummary {

    private QualitySummary() {}

    /**
     * @param counted every attempt that was not discarded for a date rollover
     * @param evaluated those of them that are not errors
     */
    static Map<String, Object> of(List<Attempt> counted, List<Attempt> evaluated, CaseResults cases) {
        Map<String, Long> counts = Stats.verdictCounts(counted);
        long pass = counts.get("pass");
        long fail = counts.get("fail");

        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("verdictCounts", counts);
        quality.put("passRate", pass + fail == 0 ? null : (double) pass / (pass + fail));
        quality.put("errorRate", counted.isEmpty() ? null : (double) counts.get("error") / counted.size());
        quality.put("precision", Stats.meanWithCount(cases.precisions()));
        quality.put("recall", Stats.meanWithCount(cases.recalls()));
        quality.put("f1", Stats.meanWithCount(cases.f1s()));
        quality.put("falsePositiveRate", falsePositiveRate(evaluated));
        quality.put("outcomeConfusion", outcomeConfusion(evaluated));
        quality.put("passRateByCovers", passRateByCovers(evaluated));
        return quality;
    }

    private static Double falsePositiveRate(List<Attempt> evaluated) {
        List<Attempt> expectingNone = evaluated.stream()
                .filter(a -> a.matching != null && a.matching.expected == 0)
                .toList();
        if (expectingNone.isEmpty()) {
            return null;
        }
        long producedSome =
                expectingNone.stream().filter(a -> a.matching.actual > 0).count();
        return (double) producedSome / expectingNone.size();
    }

    private static Map<String, Map<String, Long>> outcomeConfusion(List<Attempt> evaluated) {
        Map<String, Map<String, Long>> confusion = new TreeMap<>();
        for (Attempt a : evaluated) {
            if (a.expected != null) {
                confusion
                        .computeIfAbsent(a.expected.path("messageOutcome").asString(), k -> new TreeMap<>())
                        .merge(String.valueOf(a.actual.outcome), 1L, Long::sum);
            }
        }
        return confusion;
    }

    private static Map<String, Double> passRateByCovers(List<Attempt> evaluated) {
        Map<String, List<Attempt>> groups = new TreeMap<>();
        evaluated.forEach(
                a -> groups.computeIfAbsent(a.covers, k -> new ArrayList<>()).add(a));
        Map<String, Double> rates = new TreeMap<>();
        groups.forEach((covers, group) -> rates.put(
                covers,
                (double) group.stream().filter(a -> "pass".equals(a.verdict)).count() / group.size()));
        return rates;
    }
}
