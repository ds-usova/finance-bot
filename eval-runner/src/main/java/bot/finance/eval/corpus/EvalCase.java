package bot.finance.eval.corpus;

import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/** One case of the corpus, joined with its steps file. */
public record EvalCase(
        String id,
        String covers,
        String message,
        List<LearningEntry> learning,
        JsonNode expected,
        JsonNode executionLimits,
        String blockedReason,
        List<Step> steps,
        List<Step> finallySteps) {

    public Optional<LearningEntry> learningEntry(String learningId) {
        return learning.stream().filter(entry -> entry.id().equals(learningId)).findFirst();
    }

    public boolean blocked() {
        return blockedReason != null;
    }
}
