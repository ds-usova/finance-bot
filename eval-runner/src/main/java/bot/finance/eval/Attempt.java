package bot.finance.eval;

import bot.finance.eval.ledger.Proposal;
import bot.finance.eval.observe.McpCall;
import bot.finance.eval.observe.TargetUsage;
import bot.finance.eval.oracle.Check;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** One executed attempt of a case repetition, written as it stands; a value never collected stays null. */
public final class Attempt {

    public String caseId;
    public String covers;
    public int repetition;
    public int attempt;
    public String attemptStatus = "completed";
    public String startedAt;
    public String finishedAt;
    public String expectedDate;
    public Map<String, Long> messageIds = new LinkedHashMap<>();
    public JsonNode expected;
    public Actual actual = new Actual();
    public List<Check> checks;
    public Matching matching;
    public String verdict;
    public Quality qualityMetrics = new Quality();
    public Measurements measurements = new Measurements();
    public List<Failure> errors = new ArrayList<>();
    public List<Failure> warnings = new ArrayList<>();

    public static final class Actual {
        public String outcome;
        public List<Proposal> proposals;
        public List<McpCall> mcpCalls;
    }

    public static final class Matching {
        public int expected;
        public int actual;
        public int matched;
        public Map<Integer, Long> pairs;
    }

    public static final class Quality {
        public Double precision;
        public Double recall;
        public Double f1;
    }

    public static final class Measurements {
        public Long targetTurnMs;
        public Map<String, Learning> learning = new LinkedHashMap<>();
        public TargetUsage usage;

        public Learning learning(String learningId) {
            return learning.computeIfAbsent(learningId, id -> new Learning());
        }
    }

    public static final class Learning {
        public Long turnMs;
        public Long availabilityMs;
    }

    public record Failure(String stage, String type, String message, Integer status) {

        public static Failure of(Throwable e) {
            return e instanceof EvalException eval
                    ? new Failure(eval.stage(), e.getClass().getSimpleName(), e.getMessage(), eval.status())
                    : new Failure("runner", e.getClass().getSimpleName(), e.getMessage(), null);
        }
    }
}
