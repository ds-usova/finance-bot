package bot.finance.eval.oracle;

import bot.finance.eval.ledger.Proposal;
import bot.finance.eval.observe.McpCall;
import bot.finance.eval.oracle.Constraint.PathConstraint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Compares one attempt's outcome, proposals and MCP calls with the case's expectations, as scoring.md defines. */
public final class Oracle {

    private Oracle() {}

    /**
     * @param expected the case's {@code expected}, date tokens already resolved
     * @param calls the target's MCP calls, or null where no call record could be read
     */
    public static Evaluation evaluate(
            JsonNode expected, String outcome, List<Proposal> proposals, List<McpCall> calls) {
        List<Check> checks = new ArrayList<>();

        String expectedOutcome = expected.path("messageOutcome").asString();
        checks.add(Check.of("messageOutcome", expectedOutcome, outcome, expectedOutcome.equals(outcome)));

        int expectedCount = expected.path("proposals").path("count").asInt();
        int actualCount = proposals.size();
        checks.add(Check.of("proposals.count", expectedCount, actualCount, expectedCount == actualCount));

        JsonNode items = expected.path("proposals").path("items");
        Map<Integer, Long> pairs = null;
        int matched;
        if (items.isArray()) {
            pairs = match(items, proposals);
            for (int index = 0; index < items.size(); index++) {
                Long proposalId = pairs.get(index);
                checks.add(
                        Check.of("proposals.items[" + index + "]", items.get(index), proposalId, proposalId != null));
            }
            matched = pairs.size();
        } else {
            matched = Math.min(expectedCount, actualCount);
        }

        if (expected.path("proposalRelations").path("distinctCategories").asBoolean(false)) {
            long distinct = proposals.stream()
                    .mapToLong(Proposal::categoryId)
                    .distinct()
                    .count();
            checks.add(Check.of(
                    "proposalRelations.distinctCategories", true, distinct == actualCount, distinct == actualCount));
        }

        JsonNode mcp = expected.path("mcpCalls");
        for (String tool : mcp.propertyNames()) {
            checks.addAll(mcpChecks(tool, mcp.get(tool), calls));
        }

        String verdict = checks.stream().anyMatch(check -> check.status().equals("not-evaluated"))
                ? "error"
                : checks.stream().allMatch(check -> check.status().equals("pass")) ? "pass" : "fail";

        return new Evaluation(
                checks,
                expectedCount,
                actualCount,
                matched,
                pairs,
                verdict,
                actualCount == 0 ? null : (double) matched / actualCount,
                expectedCount == 0 ? null : (double) matched / expectedCount,
                expectedCount + actualCount == 0 ? null : 2.0 * matched / (expectedCount + actualCount));
    }

    private static List<Check> mcpChecks(String tool, JsonNode expectation, List<McpCall> calls) {
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
            switch (name) {
                case "count" ->
                    checks.add(Check.of(
                            prefix + ".count", value.asInt(), attempted.size(), attempted.size() == value.asInt()));
                case "maximumCount" ->
                    checks.add(Check.of(
                            prefix + ".maximumCount",
                            value.asInt(),
                            attempted.size(),
                            attempted.size() <= value.asInt()));
                case "acceptedCount" ->
                    checks.add(Check.of(
                            prefix + ".acceptedCount",
                            value.asInt(),
                            accepted.size(),
                            accepted.size() == value.asInt()));
                case "minimumAcceptedCount" ->
                    checks.add(Check.of(
                            prefix + ".minimumAcceptedCount",
                            value.asInt(),
                            accepted.size(),
                            accepted.size() >= value.asInt()));
                case "arguments" -> {
                    for (PathConstraint argument : Constraint.flatten(value)) {
                        List<String> actual = accepted.stream()
                                .map(call -> text(call.arguments().path(argument.path())))
                                .toList();
                        boolean holds = !actual.isEmpty()
                                && actual.stream().allMatch(v -> Constraint.holds(argument.constraint(), v));
                        checks.add(Check.of(
                                prefix + ".arguments." + argument.path(), argument.constraint(), actual, holds));
                    }
                }
                default -> throw new IllegalArgumentException("unknown MCP expectation " + prefix + "." + name);
            }
        }
        return checks;
    }

    /**
     * The largest one-to-one matching of expected items to proposals that satisfy every constraint of the item,
     * found by augmenting paths, as expected index to proposal id.
     */
    static Map<Integer, Long> match(JsonNode items, List<Proposal> proposals) {
        boolean[][] fits = new boolean[items.size()][proposals.size()];
        for (int item = 0; item < items.size(); item++) {
            List<PathConstraint> constraints = Constraint.flatten(items.get(item));
            for (int proposal = 0; proposal < proposals.size(); proposal++) {
                Proposal candidate = proposals.get(proposal);
                fits[item][proposal] =
                        constraints.stream().allMatch(c -> Constraint.holds(c.constraint(), candidate.field(c.path())));
            }
        }

        int[] itemOfProposal = new int[proposals.size()];
        Arrays.fill(itemOfProposal, -1);
        for (int item = 0; item < items.size(); item++) {
            augment(item, fits, itemOfProposal, new boolean[proposals.size()]);
        }

        Map<Integer, Long> pairs = new LinkedHashMap<>();
        for (int item = 0; item < items.size(); item++) {
            for (int proposal = 0; proposal < proposals.size(); proposal++) {
                if (itemOfProposal[proposal] == item) {
                    pairs.put(item, proposals.get(proposal).id());
                }
            }
        }
        return pairs;
    }

    private static boolean augment(int item, boolean[][] fits, int[] itemOfProposal, boolean[] visited) {
        for (int proposal = 0; proposal < itemOfProposal.length; proposal++) {
            if (fits[item][proposal] && !visited[proposal]) {
                visited[proposal] = true;
                if (itemOfProposal[proposal] < 0 || augment(itemOfProposal[proposal], fits, itemOfProposal, visited)) {
                    itemOfProposal[proposal] = item;
                    return true;
                }
            }
        }
        return false;
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }
}
