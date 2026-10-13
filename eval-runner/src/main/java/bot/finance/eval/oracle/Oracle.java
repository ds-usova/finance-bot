package bot.finance.eval.oracle;

import bot.finance.eval.ledger.Proposal;
import bot.finance.eval.observe.McpCall;
import java.util.ArrayList;
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
        int matched = Math.min(expectedCount, actualCount);
        if (items.isArray()) {
            pairs = ProposalMatcher.match(items, proposals);
            for (int index = 0; index < items.size(); index++) {
                Long proposalId = pairs.get(index);
                checks.add(
                        Check.of("proposals.items[" + index + "]", items.get(index), proposalId, proposalId != null));
            }
            matched = pairs.size();
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
            checks.addAll(McpExpectations.check(tool, mcp.get(tool), calls));
        }

        return new Evaluation(
                checks,
                expectedCount,
                actualCount,
                matched,
                pairs,
                verdict(checks),
                actualCount == 0 ? null : (double) matched / actualCount,
                expectedCount == 0 ? null : (double) matched / expectedCount,
                expectedCount + actualCount == 0 ? null : 2.0 * matched / (expectedCount + actualCount));
    }

    private static String verdict(List<Check> checks) {
        if (checks.stream().anyMatch(check -> check.status().equals("not-evaluated"))) {
            return "error";
        }
        return checks.stream().allMatch(check -> check.status().equals("pass")) ? "pass" : "fail";
    }
}
