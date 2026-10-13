package bot.finance.eval.corpus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cases a run executes, and those it reports as blocked with their reason. A case declaring
 * {@code executionLimits} is blocked unless the run says call budgets are enforced.
 */
public record CaseSelection(List<EvalCase> runnable, Map<String, String> blocked) {

    public static CaseSelection of(List<EvalCase> corpus, List<String> idPrefixes, boolean budgetEnforced) {
        List<EvalCase> runnable = new ArrayList<>();
        Map<String, String> blocked = new LinkedHashMap<>();
        for (EvalCase evalCase : corpus) {
            if (!idPrefixes.isEmpty() && idPrefixes.stream().noneMatch(evalCase.id()::startsWith)) {
                continue;
            }
            if (evalCase.blocked()) {
                blocked.put(evalCase.id(), evalCase.blockedReason());
            } else if (evalCase.executionLimits() != null && !budgetEnforced) {
                blocked.put(evalCase.id(), "declares executionLimits; call-budget enforcement is unavailable");
            } else {
                runnable.add(evalCase);
            }
        }
        return new CaseSelection(runnable, blocked);
    }
}
