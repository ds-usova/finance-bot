package bot.finance.eval.report;

import bot.finance.eval.Attempt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The run's aggregates, computed from its attempt records alone. */
final class Summary {

    private Summary() {}

    static Map<String, Object> of(List<Attempt> attempts, Map<String, String> blocked, long runMs) {
        List<Attempt> counted = attempts.stream()
                .filter(a -> a.attemptStatus.equals("completed"))
                .toList();
        List<Attempt> evaluated =
                counted.stream().filter(a -> !"error".equals(a.verdict)).toList();
        CaseResults cases = CaseResults.of(counted);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("attempts", attempts.size());
        summary.put("discardedRolloverAttempts", attempts.size() - counted.size());
        summary.put("blockedCases", blocked);
        summary.put("quality", QualitySummary.of(counted, evaluated, cases));
        summary.put("performance", PerformanceSummary.of(evaluated, runMs));
        summary.put("cases", cases.byCase());
        return summary;
    }
}
