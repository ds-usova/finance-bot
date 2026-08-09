package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.SpendingQueryEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.rows.SpendingQueryRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.value.IncomingMessageId;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Drives {@code POST /mcp} - the {@code tools/call summarize_spending} JSON-RPC method - end to end against the
 * fully wired application.
 */
class SummarizeSpendingMcpToolSystemTest extends AbstractSystemTest {

    private static final String FROM = "2026-07-01";
    private static final String TO = "2026-07-31";

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    private Response callSummarizeSpending(String token, String requestBody) {
        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(McpRequests.ACCEPT_HEADER)
                .header("Authorization", "Bearer " + token)
                .body(requestBody)
                .when()
                .post("/mcp");
        logResponse(response);
        return response;
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when tools/call summarize_spending is posted with a first and last day - then that period "
                + "is answered and recorded")
        void whenToolCallNamesAPeriod_thenThatPeriodIsAnsweredAndRecorded() {
            String externalId = "summarize-spending-happy-path-user";
            long userId = UserRowUtils.storedUserId(userEntityRepository, externalId);
            IncomingMessageId reference =
                    IncomingMessageId.of(java.util.UUID.randomUUID().toString());
            String token = McpTokens.tokenFor(accessTokenMinter, externalId, reference);

            String requestBody = McpRequests.summarizeSpending(FROM, TO);

            Response response = callSummarizeSpending(token, requestBody);

            assertThat(response.statusCode()).as("HTTP status").isEqualTo(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("tool result isError")
                    .isNotEqualTo(Boolean.TRUE);

            String toolResultText = response.jsonPath().getString("result.content[0].text");
            assertThat(toolResultText).as("tool result text").isNotNull();
            JsonPath toolResult = new JsonPath(toolResultText);
            assertThat(toolResult.getString("from")).as("returned from day").isEqualTo(FROM);
            assertThat(toolResult.getString("to")).as("returned to day").isEqualTo(TO);
            assertThat(toolResultText).as("tool result text carries no amount").doesNotContain("amount");

            List<SpendingQueryEntity> rows = SpendingQueryRowUtils.spendingQueryRowsFor(jdbcAggregateTemplate, userId);
            assertThat(rows)
                    .as("stored spending_query rows for user %s", userId)
                    .hasSize(1);
            SpendingQueryEntity row = rows.get(0);
            assertThat(row.incomingMessageId())
                    .as("stored query's message reference matches the token's mrf claim")
                    .isEqualTo(reference.value());
            assertThat(row.periodStart()).as("stored query's period start").isEqualTo(LocalDate.parse(FROM));
            assertThat(row.periodEnd()).as("stored query's period end").isEqualTo(LocalDate.parse(TO));
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @DisplayName("when summarize_spending is posted with a last day before the first - then the period is "
                + "refused and nothing recorded")
        void whenLastDayIsBeforeFirst_thenNothingIsRecordedAndPeriodIsRefused() {
            String externalId = "summarize-spending-unhappy-path-user";
            long userId = UserRowUtils.storedUserId(userEntityRepository, externalId);
            String token = McpTokens.tokenFor(accessTokenMinter, externalId);

            String requestBody = McpRequests.summarizeSpending(TO, FROM);

            Response response = callSummarizeSpending(token, requestBody);

            assertThat(response.statusCode()).as("HTTP status").isEqualTo(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("tool result isError")
                    .isTrue();

            String toolResultText = response.jsonPath().getString("result.content[0].text");
            assertThat(toolResultText)
                    .as("tool error message says the period ends before it starts")
                    .containsIgnoringCase("ends before it starts");

            assertThat(SpendingQueryRowUtils.spendingQueryRowsFor(jdbcAggregateTemplate, userId))
                    .as("stored spending_query rows for user %s", userId)
                    .isEmpty();
        }
    }
}
