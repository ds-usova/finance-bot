package bot.finance.eval.report;

import bot.finance.eval.Attempt;
import bot.finance.eval.observe.TargetUsage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Target-turn timing, provider calls and tokens over the attempts that are not errors. */
final class PerformanceSummary {

    private PerformanceSummary() {}

    static Map<String, Object> of(List<Attempt> evaluated, long runMs) {
        Map<String, Object> performance = new LinkedHashMap<>();
        performance.put("runMs", runMs);
        performance.put("targetTurnMs", Stats.of(Stats.values(evaluated, a -> a.measurements.targetTurnMs)));
        performance.put("providerChatCalls", usage(evaluated, TargetUsage::providerChatCalls));
        performance.put("providerChatSeconds", usage(evaluated, TargetUsage::providerChatSeconds));
        performance.put("inputTokens", usage(evaluated, TargetUsage::inputTokens));
        performance.put("outputTokens", usage(evaluated, TargetUsage::outputTokens));
        performance.put("ledgerToolCalls", usage(evaluated, TargetUsage::ledgerToolCalls));
        return performance;
    }

    private static Map<String, Object> usage(List<Attempt> evaluated, Function<TargetUsage, Double> value) {
        return Stats.of(
                Stats.values(evaluated, a -> a.measurements.usage == null ? null : value.apply(a.measurements.usage)));
    }
}
