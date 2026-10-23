package bot.finance.eval.report;

import bot.finance.eval.Attempt;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The extraction mode a run measured, read from its attempts' usage records. */
final class RunMode {

    private RunMode() {}

    static String of(List<Attempt> attempts) {
        Set<String> modes = new HashSet<>();
        for (Attempt attempt : attempts) {
            if (attempt.measurements.usage != null && attempt.measurements.usage.extractionMode() != null) {
                modes.add(attempt.measurements.usage.extractionMode());
            }
        }
        if (modes.isEmpty()) {
            return null;
        }
        return modes.size() == 1 ? modes.iterator().next() : "mixed";
    }
}
