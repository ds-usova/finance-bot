package bot.finance.eval.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.eval.ledger.Proposal;
import bot.finance.eval.observe.TurnCall;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

class OracleTest {

    private static final YAMLMapper YAML = new YAMLMapper();

    private static JsonNode yaml(String text) {
        return YAML.readTree(text);
    }

    private static Proposal proposal(long id, String amount, String merchant, String category, long categoryId) {
        return new Proposal(id, amount, "HUF", merchant, "coffee", categoryId, category, "Dining");
    }

    private static TurnCall call(String tool, boolean accepted, String arguments) {
        return new TurnCall(tool, accepted, "web:1", YAML.readTree(arguments));
    }

    @Test
    void passesWhenOutcomeCountAndEveryItemMatch() {
        JsonNode expected = yaml(
                """
                messageOutcome: RECORDED
                proposals:
                  count: 2
                  items:
                    - {"money":{"amount":{"equals":"500.00"}}}
                    - {"money":{"amount":{"equals":"850.00"}}}
                """);

        Evaluation evaluation = Oracle.evaluate(
                expected,
                "RECORDED",
                List.of(proposal(7, "850.00", null, "Cafés", 1), proposal(8, "500.00", null, "Buses", 2)),
                List.of());

        assertThat(evaluation.verdict()).isEqualTo("pass");
        assertThat(evaluation.matchedPairs()).containsEntry(0, 8L).containsEntry(1, 7L);
        assertThat(evaluation.f1()).isEqualTo(1.0);
    }

    @Test
    void findsTheLargestMatchingRatherThanTheFirst() {
        // Proposal 7 fits both items; taking it for item 0 first would leave item 1 unmatched.
        JsonNode expected = yaml(
                """
                messageOutcome: RECORDED
                proposals:
                  count: 2
                  items:
                    - {"merchant":{"present":true}}
                    - {"merchant":{"equals":"Corner Shop"}}
                """);

        Evaluation evaluation = Oracle.evaluate(
                expected,
                "RECORDED",
                List.of(proposal(7, "1.00", "Corner Shop", "Cafés", 1), proposal(8, "1.00", "Other", "Cafés", 1)),
                List.of());

        assertThat(evaluation.matchedCount()).isEqualTo(2);
        assertThat(evaluation.verdict()).isEqualTo("pass");
    }

    @Test
    void failsAndScoresPartiallyWhenAnItemFindsNoProposal() {
        JsonNode expected = yaml(
                """
                messageOutcome: RECORDED
                proposals:
                  count: 1
                  items:
                    - {"merchant":{"absent":true}}
                """);

        Evaluation evaluation = Oracle.evaluate(
                expected, "RECORDED", List.of(proposal(7, "1.00", "Corner Shop", "Cafés", 1)), List.of());

        assertThat(evaluation.verdict()).isEqualTo("fail");
        assertThat(evaluation.precision()).isEqualTo(0.0);
        assertThat(evaluation.recall()).isEqualTo(0.0);
    }

    @Test
    void countOnlyCaseMatchesTheSmallerCountAndLeavesUndefinedMetricsNull() {
        JsonNode expected = yaml(
                """
                messageOutcome: NOTHING_IDENTIFIED
                proposals:
                  count: 0
                """);

        Evaluation evaluation = Oracle.evaluate(expected, "NOTHING_IDENTIFIED", List.of(), List.of());

        assertThat(evaluation.verdict()).isEqualTo("pass");
        assertThat(evaluation.matchedPairs()).isNull();
        assertThat(evaluation.precision()).isNull();
        assertThat(evaluation.recall()).isNull();
        assertThat(evaluation.f1()).isNull();
    }

    @Test
    void distinctCategoriesFailsWhenTwoProposalsShareOne() {
        JsonNode expected = yaml(
                """
                messageOutcome: RECORDED
                proposals:
                  count: 2
                proposalRelations:
                  distinctCategories: true
                """);

        Evaluation evaluation = Oracle.evaluate(
                expected,
                "RECORDED",
                List.of(proposal(7, "1.00", null, "Cafés", 1), proposal(8, "2.00", null, "Cafés", 1)),
                List.of());

        assertThat(evaluation.verdict()).isEqualTo("fail");
    }

    @Test
    void mcpCountsSeparateAttemptedFromAcceptedCalls() {
        JsonNode expected = yaml(
                """
                messageOutcome: RECORDED
                proposals:
                  count: 0
                mcpCalls:
                  create_expense_proposal:
                    acceptedCount: 1
                    maximumCount: 2
                  list_categories:
                    minimumAcceptedCount: 1
                  summarize_spending:
                    count: 0
                """);

        Evaluation evaluation = Oracle.evaluate(
                expected,
                "RECORDED",
                List.of(),
                List.of(
                        call("create_expense_proposal", false, "{}"),
                        call("create_expense_proposal", true, "{}"),
                        call("list_categories", true, "{}")));

        assertThat(evaluation.verdict()).isEqualTo("pass");
    }

    @Test
    void mcpArgumentsResolveDateTokensAgainstTheRunDate() {
        JsonNode expected = Constraint.resolved(
                yaml(
                        """
                        messageOutcome: ANSWERED
                        proposals:
                          count: 0
                        mcpCalls:
                          summarize_spending:
                            acceptedCount: 1
                            arguments:
                              from: {equals: previous-week-monday}
                              to: {equals: previous-week-sunday}
                        """),
                LocalDate.of(2026, 10, 13));

        Evaluation evaluation = Oracle.evaluate(
                expected,
                "ANSWERED",
                List.of(),
                List.of(call("summarize_spending", true, "{\"from\":\"2026-10-05\",\"to\":\"2026-10-11\"}")));

        assertThat(evaluation.verdict()).isEqualTo("pass");
    }

    @Test
    void missingCallRecordsLeaveTheMcpChecksUnevaluatedAndTheVerdictAnError() {
        JsonNode expected = yaml(
                """
                messageOutcome: ANSWERED
                proposals:
                  count: 0
                mcpCalls:
                  summarize_spending:
                    acceptedCount: 1
                """);

        Evaluation evaluation = Oracle.evaluate(expected, "ANSWERED", List.of(), null);

        assertThat(evaluation.verdict()).isEqualTo("error");
        assertThat(evaluation.checks()).anyMatch(check -> check.status().equals("not-evaluated"));
    }
}
