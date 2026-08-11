package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.CategoryEntity;
import bot.finance.adapter.persistence.ExpenseProposalEntity;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.application.port.UserRepository;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.domain.model.User;
import bot.finance.domain.value.Grouping;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

/**
 * Drives {@code POST /mcp} - the {@code tools/call create_expense_proposal} JSON-RPC method - end to end against
 * the fully wired application. The user and their category tree are seeded through the wired {@link UserRepository}
 * with {@link Grouping#defaults()}, the tree's only writer, never through a {@code bot.finance.common} row helper.
 */
class CreateExpenseProposalMcpToolSystemTest extends AbstractSystemTest {

    private static final String DESCRIPTION = "Milk";
    private static final String MERCHANT = "Corner Shop";
    private static final String AMOUNT = "7200";
    private static final String CURRENCY_CODE = "HUF";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    private User seedUserWithDefaultCategories(String externalId) {
        return userRepository.create(User.newUser(externalId), Grouping.defaults());
    }

    private CategoryEntity storedCategory(long userId, String name) {
        return CategoryRowUtils.categoryRowsFor(jdbcAggregateTemplate, userId).stream()
                .filter(row -> row.name().equals(name))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException("no seeded category named " + name + " for user " + userId));
    }

    private Response callCreateExpenseProposal(String token, String requestBody) {
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
        @Disabled("GI07: UserRepositoryAdapter.findById answers Optional.empty(), so the authenticated caller is "
                + "always refused as unknown")
        @DisplayName("when create_expense_proposal names a category under its own grouping - then the proposal is "
                + "stored and returned")
        void whenToolCallNamesCategoryUnderItsGrouping_thenProposalIsStoredAndReturned() {
            User user = seedUserWithDefaultCategories("create-expense-proposal-happy-path-user");
            long userId = user.id().orElseThrow();
            long supermarketsCategoryId = storedCategory(userId, "Supermarkets").id();
            String token = McpTokens.tokenFor(accessTokenMinter, userId);

            String requestBody = McpRequests.createExpenseProposal(
                    "Supermarkets", "Groceries", DESCRIPTION, MERCHANT, AMOUNT, CURRENCY_CODE);

            Response response = callCreateExpenseProposal(token, requestBody);

            assertThat(response.statusCode()).as("HTTP status").isEqualTo(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("tool result isError")
                    .isNotEqualTo(Boolean.TRUE);

            String toolResultText = response.jsonPath().getString("result.content[0].text");
            assertThat(toolResultText).as("tool result text").isNotNull();
            JsonPath toolResult = new JsonPath(toolResultText);
            assertThat(toolResult.getString("category")).as("returned category").isEqualTo("Supermarkets");
            assertThat(toolResult.getString("description"))
                    .as("returned description")
                    .isEqualTo(DESCRIPTION);
            assertThat(toolResult.getString("merchant")).as("returned merchant").isEqualTo(MERCHANT);
            assertThat(toolResult.getString("amount")).as("returned amount").isEqualTo("7200.00");
            assertThat(toolResult.getString("currencyCode"))
                    .as("returned currencyCode")
                    .isEqualTo(CURRENCY_CODE);

            List<ExpenseProposalEntity> rows =
                    ExpenseProposalRowUtils.expenseProposalRowsFor(jdbcAggregateTemplate, userId);
            assertThat(rows)
                    .as("stored expense_proposal rows for user %s", userId)
                    .hasSize(1);
            ExpenseProposalEntity row = rows.get(0);
            assertThat(row.categoryId()).as("stored proposal's category id").isEqualTo(supermarketsCategoryId);
            assertThat(row.description()).as("stored proposal's description").isEqualTo(DESCRIPTION);
            assertThat(row.merchant()).as("stored proposal's merchant").isEqualTo(MERCHANT);
            assertThat(row.amountMinorUnits())
                    .as("stored proposal's minor units")
                    .isEqualTo(720000L);
            assertThat(row.currencyCode()).as("stored proposal's currency code").isEqualTo(CURRENCY_CODE);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @Test
        @Disabled("GI07: UserRepositoryAdapter.findById answers Optional.empty(), so the tool answers \"the user is "
                + "unknown\" before the grouping mismatch is ever checked")
        @DisplayName("when create_expense_proposal names the wrong grouping - then nothing is stored and the "
                + "mismatch is named")
        void whenToolCallNamesCategoryUnderTheWrongGrouping_thenNothingIsStoredAndMismatchIsNamed() {
            User user = seedUserWithDefaultCategories("create-expense-proposal-unhappy-path-user");
            long userId = user.id().orElseThrow();
            String token = McpTokens.tokenFor(accessTokenMinter, userId);

            String requestBody = McpRequests.createExpenseProposal(
                    "Supermarkets", "Dining", DESCRIPTION, MERCHANT, AMOUNT, CURRENCY_CODE);

            Response response = callCreateExpenseProposal(token, requestBody);

            assertThat(response.statusCode()).as("HTTP status").isEqualTo(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("tool result isError")
                    .isTrue();

            String toolResultText = response.jsonPath().getString("result.content[0].text");
            assertThat(toolResultText)
                    .as("tool error message names the grouping mismatch")
                    .contains("no category named Supermarkets under grouping Dining is stored for this user");

            List<ExpenseProposalEntity> rows =
                    ExpenseProposalRowUtils.expenseProposalRowsFor(jdbcAggregateTemplate, userId);
            assertThat(rows)
                    .as("stored expense_proposal rows for user %s", userId)
                    .isEmpty();
        }

        @Test
        @Disabled("GI07: UserRepositoryAdapter.findById answers Optional.empty(), so the tool answers \"the user is "
                + "unknown\" before the missing grouping is ever checked")
        @DisplayName("when create_expense_proposal is posted with no grouping - then nothing is stored and the "
                + "grouping is named")
        void whenToolCallHasNoGrouping_thenNothingIsStoredAndMissingGroupingIsNamed() {
            User user = seedUserWithDefaultCategories("create-expense-proposal-no-grouping-user");
            long userId = user.id().orElseThrow();
            String token = McpTokens.tokenFor(accessTokenMinter, userId);

            String requestBody =
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "method": "tools/call",
                      "params": {
                        "name": "create_expense_proposal",
                        "arguments": {
                          "category": "Supermarkets",
                          "description": "%s",
                          "merchant": "%s",
                          "amount": "%s",
                          "currencyCode": "%s"
                        }
                      }
                    }
                    """
                            .formatted(DESCRIPTION, MERCHANT, AMOUNT, CURRENCY_CODE);

            Response response = callCreateExpenseProposal(token, requestBody);

            assertThat(response.statusCode()).as("HTTP status").isEqualTo(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("tool result isError")
                    .isTrue();

            String toolResultText = response.jsonPath().getString("result.content[0].text");
            assertThat(toolResultText)
                    .as("tool error message names the missing grouping")
                    .containsIgnoringCase("grouping");

            List<ExpenseProposalEntity> rows =
                    ExpenseProposalRowUtils.expenseProposalRowsFor(jdbcAggregateTemplate, userId);
            assertThat(rows)
                    .as("stored expense_proposal rows for user %s", userId)
                    .isEmpty();
        }
    }
}
