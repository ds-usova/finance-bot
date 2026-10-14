package bot.finance.eval.report;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Two runs' summaries side by side, as a Markdown table, and the cases whose pass count moved. */
public final class Comparison {

    private static final List<String[]> ROWS = List.of(
            new String[] {"pass rate", "quality.passRate"},
            new String[] {"error rate", "quality.errorRate"},
            new String[] {"precision", "quality.precision.mean"},
            new String[] {"recall", "quality.recall.mean"},
            new String[] {"F1", "quality.f1.mean"},
            new String[] {"false-positive rate", "quality.falsePositiveRate"},
            new String[] {"turn ms, median", "performance.targetTurnMs.median"},
            new String[] {"turn ms, p90", "performance.targetTurnMs.p90"},
            new String[] {"provider chat calls, median", "performance.providerChatCalls.median"},
            new String[] {"provider chat calls, mean", "performance.providerChatCalls.mean"},
            new String[] {"provider chat s, median", "performance.providerChatSeconds.median"},
            new String[] {"input tokens, median", "performance.inputTokens.median"},
            new String[] {"output tokens, median", "performance.outputTokens.median"},
            new String[] {"run ms", "performance.runMs"});

    private Comparison() {}

    public static String render(JsonNode before, JsonNode after) {
        StringBuilder out = new StringBuilder();
        out.append("| Measure | ")
                .append(before.path("runId").asString())
                .append(" | ")
                .append(after.path("runId").asString())
                .append(" | Change |\n|---|---|---|---|\n");
        JsonNode leftMode = before.path("summary").path("extractionMode");
        JsonNode rightMode = after.path("summary").path("extractionMode");
        out.append(row("extraction mode", mode(leftMode), mode(rightMode), modeChange(leftMode, rightMode)));
        for (String[] measure : ROWS) {
            JsonNode left = at(before.path("summary"), measure[1]);
            JsonNode right = at(after.path("summary"), measure[1]);
            out.append(row(measure[0], format(left), format(right), change(left, right)));
        }

        out.append("\n| Case | Passes before | Passes after |\n|---|---|---|\n");
        JsonNode beforeCases = before.path("summary").path("cases");
        JsonNode afterCases = after.path("summary").path("cases");
        Set<String> ids = new LinkedHashSet<>(beforeCases.propertyNames());
        ids.addAll(afterCases.propertyNames());
        for (String id : ids) {
            JsonNode left = beforeCases.path(id).path("verdictCounts").path("pass");
            JsonNode right = afterCases.path(id).path("verdictCounts").path("pass");
            if (!left.equals(right)) {
                out.append(row(id, format(left), format(right)));
            }
        }
        return out.toString();
    }

    private static String row(String... cells) {
        return "| " + String.join(" | ", cells) + " |\n";
    }

    private static JsonNode at(JsonNode node, String path) {
        for (String part : path.split("\\.")) {
            node = node.path(part);
        }
        return node;
    }

    private static String mode(JsonNode value) {
        return value.isTextual() ? value.asString() : "n/a";
    }

    private static String modeChange(JsonNode left, JsonNode right) {
        return left.isTextual() && right.isTextual() && !left.equals(right) ? "differs" : "";
    }

    private static String format(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return "n/a";
        }
        double number = value.asDouble();
        return number == Math.rint(number) && Math.abs(number) >= 1
                ? Long.toString((long) number)
                : String.format(Locale.ROOT, "%.3f", number);
    }

    private static String change(JsonNode left, JsonNode right) {
        if (!left.isNumber() || !right.isNumber()) {
            return "n/a";
        }
        double from = left.asDouble();
        double to = right.asDouble();
        String delta = String.format(Locale.ROOT, "%+.3f", to - from);
        return from == 0 ? delta : delta + String.format(Locale.ROOT, " (%+.1f%%)", (to - from) / from * 100);
    }
}
