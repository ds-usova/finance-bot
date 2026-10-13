package bot.finance.eval.oracle;

import bot.finance.eval.observe.McpCall;
import bot.finance.eval.oracle.Constraint.PathConstraint;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Checks a case's {@code mcpCalls} expectations for one tool. Counts include rejected calls; accepted counts and
 * argument constraints read only the calls the ledger accepted.
 */
final class McpExpectations {

    private McpExpectations() {}

    /** @param calls the target's calls, or null where none could be read, which leaves every check unevaluated */
    static List<Check> check(String tool, JsonNode expectation, List<McpCall> calls) {
        String prefix = "mcpCalls." + tool;
        List<Check> checks = new ArrayList<>();
        if (calls == null) {
            for (String name : expectation.propertyNames()) {
                checks.add(Check.notEvaluated(prefix + "." + name, expectation.get(name)));
            }
            return checks;
        }

        List<McpCall> attempted =
                calls.stream().filter(call -> call.tool().equals(tool)).toList();
        List<McpCall> accepted = attempted.stream().filter(McpCall::accepted).toList();

        for (String name : expectation.propertyNames()) {
            JsonNode value = expectation.get(name);
            String path = prefix + "." + name;
            switch (name) {
                case "count" -> checks.add(count(path, value, attempted.size(), attempted.size() == value.asInt()));
                case "maximumCount" ->
                    checks.add(count(path, value, attempted.size(), attempted.size() <= value.asInt()));
                case "acceptedCount" ->
                    checks.add(count(path, value, accepted.size(), accepted.size() == value.asInt()));
                case "minimumAcceptedCount" ->
                    checks.add(count(path, value, accepted.size(), accepted.size() >= value.asInt()));
                case "arguments" -> checks.addAll(arguments(path, value, accepted));
                default -> throw new IllegalArgumentException("unknown MCP expectation " + path);
            }
        }
        return checks;
    }

    private static Check count(String path, JsonNode expected, int actual, boolean holds) {
        return Check.of(path, expected.asInt(), actual, holds);
    }

    /** Each argument constraint holds for every accepted call, and at least one call was accepted. */
    private static List<Check> arguments(String path, JsonNode constraints, List<McpCall> accepted) {
        List<Check> checks = new ArrayList<>();
        for (PathConstraint argument : Constraint.flatten(constraints)) {
            List<String> actual = accepted.stream()
                    .map(call -> text(call.arguments().path(argument.path())))
                    .toList();
            boolean holds =
                    !actual.isEmpty() && actual.stream().allMatch(v -> Constraint.holds(argument.constraint(), v));
            checks.add(Check.of(path + "." + argument.path(), argument.constraint(), actual, holds));
        }
        return checks;
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }
}
