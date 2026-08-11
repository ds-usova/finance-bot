package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.ExpenseProposalEntity;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.common.boot.AbstractSystemTest;
import bot.finance.common.containers.GrpcStubServer;
import bot.finance.common.fixtures.McpRequests;
import bot.finance.common.fixtures.McpTokens;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.rows.UserRowUtils;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Covers the module's first {@code SecurityFilterChain}, entered the way a real MCP client and a real monitoring
 * probe would: JSON-RPC posted to {@code /mcp}, and a plain {@code GET} against the actuator contract.
 */
class McpAuthenticationSystemTest extends AbstractSystemTest {

    @Autowired
    private AccessTokenMinter accessTokenMinter;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    /**
     * Points the AI connector's gRPC client at {@link GrpcStubServer}, which reports {@code SERVING} by default, so
     * the aggregate {@code /actuator/health} the unhappy path checks is not {@code DOWN} for a reason this class
     * has nothing to do with.
     */
    @DynamicPropertySource
    static void aiConnectorProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.client.channel.ai-connector.target", GrpcStubServer::target);
    }

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    /** Posts {@code body} to {@code /mcp}; a null {@code token} sends no {@code Authorization} header at all. */
    private Response postMcp(String token, String body) {
        RequestSpecification request = RestAssured.given()
                .contentType(ContentType.JSON)
                .accept(McpRequests.ACCEPT_HEADER)
                .body(body);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        Response response = request.when().post("/mcp");
        logResponse(response);
        return response;
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.system.McpAuthenticationSystemTest#publishedTools")
        @DisplayName("when tools/list is posted with a valid token - then each tool is listed with exactly its "
                + "own arguments")
        void whenToolsListIsPostedWithValidToken_thenEachToolIsListedWithExactlyItsOwnArguments(
                String toolName, List<String> expectedArguments, List<String> expectedRequiredArguments) {
            String externalId = "mcp-auth-tools-list-user-" + toolName;
            long userId = UserRowUtils.storedUserId(userEntityRepository, externalId);
            String token = McpTokens.tokenFor(accessTokenMinter, userId);

            Response response = postMcp(token, McpRequests.toolsList());

            response.then().statusCode(200);

            List<Map<String, Object>> tools = response.jsonPath().getList("result.tools");
            Map<String, Object> tool = tools.stream()
                    .filter(listed -> toolName.equals(listed.get("name")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(toolName + " was not listed: " + tools));

            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) tool.get("inputSchema");
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            assertThat(properties.keySet())
                    .as("%s's argument names", toolName)
                    .containsExactlyInAnyOrderElementsOf(expectedArguments);
            assertThat(inputSchema.get("required"))
                    .as("%s's required array", toolName)
                    .asInstanceOf(InstanceOfAssertFactories.LIST)
                    .containsAll(expectedRequiredArguments);
        }

        @Test
        @DisplayName("when tools/list is posted with a valid token - then create_expense_proposal publishes "
                + "amount as a string")
        void whenToolsListIsPostedWithValidToken_thenAmountIsPublishedAsAString() {
            String externalId = "mcp-auth-amount-type-user";
            long userId = UserRowUtils.storedUserId(userEntityRepository, externalId);
            String token = McpTokens.tokenFor(accessTokenMinter, userId);

            Response response = postMcp(token, McpRequests.toolsList());

            response.then().statusCode(200);
            assertThat(response.jsonPath()
                            .getMap("result.tools.find { it.name == 'create_expense_proposal' }"
                                    + ".inputSchema.properties.amount"))
                    .as("amount's published type")
                    .containsEntry("type", "string");
        }

        @Test
        @DisplayName(
                "when a tool is called with a token minted from a stored user's id - then it acts on their " + "ledger")
        void whenAToolIsCalledWithATokenMintedFromAStoredUsersId_thenItActsOnTheirLedger() {
            String externalId = "mcp-auth-acts-on-ledger-user";
            long userId = UserRowUtils.storedUserId(userEntityRepository, externalId);
            long groupingId = CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, "Groceries");
            CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, groupingId, "Supermarkets");
            String token = McpTokens.tokenFor(accessTokenMinter, userId);

            String requestBody =
                    McpRequests.createExpenseProposal("Supermarkets", "Groceries", "lunch", "Cafe", "10.00", "EUR");
            Response response = postMcp(token, requestBody);

            response.then().statusCode(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("tool result isError")
                    .isNotEqualTo(Boolean.TRUE);

            // then: the proposal is stored under the same user_id the token's subject carries
            List<ExpenseProposalEntity> rows =
                    ExpenseProposalRowUtils.expenseProposalRowsFor(jdbcAggregateTemplate, userId);
            assertThat(rows)
                    .as("stored expense_proposal rows carrying the token's subject as user_id")
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.system.McpAuthenticationSystemTest#rejectedTokens")
        @DisplayName("when tools/call is posted with a rejected token - then 401 with no tool result and no row "
                + "written")
        void whenToolsCallIsPostedWithRejectedToken_thenUnauthorizedWithNoToolResultAndNoRowWritten(
                String scenario, Function<Long, String> tokenFactory) {
            String externalId = "mcp-auth-rejected-" + scenario.replace(" ", "-");
            long userId = UserRowUtils.storedUserId(userEntityRepository, externalId);
            String token = tokenFactory == null ? null : tokenFactory.apply(userId);

            Response response = postMcp(
                    token,
                    McpRequests.createExpenseProposal("Groceries", "Groceries", "lunch", "Cafe", "10.00", "EUR"));

            response.then().statusCode(401);
            assertThat(response.getBody().asString())
                    .as("rejected response body carries no tool result")
                    .doesNotContain("\"result\"");
            assertThat(ExpenseProposalRowUtils.expenseProposalRowsFor(jdbcAggregateTemplate, userId))
                    .as("expense_proposal rows for %s", externalId)
                    .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.system.McpAuthenticationSystemTest#unknownCallerSubjects")
        @DisplayName("when a tool is called with a token naming no stored user - then both refusals match and "
                + "nothing is written")
        void whenAToolIsCalledWithATokenNamingNoStoredUser_thenBothRefusalsMatchAndNothingIsWritten(
                String scenario, long unknownUserId) {
            String token = McpTokens.tokenFor(accessTokenMinter, unknownUserId);

            Response response = postMcp(
                    token,
                    McpRequests.createExpenseProposal("Groceries", "Groceries", "lunch", "Cafe", "10.00", "EUR"));

            response.then().statusCode(200);
            assertThat(response.jsonPath().getBoolean("result.isError"))
                    .as("%s - tool result isError", scenario)
                    .isTrue();
            assertThat(response.jsonPath().getString("result.content[0].text"))
                    .as("%s - tool error message", scenario)
                    .contains("the user is unknown");
            assertThat(ExpenseProposalRowUtils.expenseProposalRowsFor(jdbcAggregateTemplate, unknownUserId))
                    .as("%s - expense_proposal rows for the unknown subject", scenario)
                    .isEmpty();
        }

        @Test
        @DisplayName("when GET /actuator/health is requested with no token - then 200")
        void whenActuatorHealthIsRequestedWithNoToken_thenOk() {
            Response response = RestAssured.given().port(managementPort).when().get("/actuator/health");
            logResponse(response);

            response.then().statusCode(200);
        }
    }

    static Stream<Arguments> publishedTools() {
        return Stream.of(
                Arguments.of(
                        "create_expense_proposal",
                        List.of("category", "grouping", "description", "merchant", "amount", "currencyCode"),
                        List.of("amount", "grouping")),
                Arguments.of("list_categories", List.of("grouping"), List.of("grouping")),
                Arguments.of("summarize_spending", List.of("from", "to"), List.of("from", "to")));
    }

    static Stream<Arguments> rejectedTokens() {
        return Stream.of(
                Arguments.of("no token", null),
                Arguments.of("expired token", (Function<Long, String>) McpTokens::expiredToken),
                Arguments.of("wrong-audience token", (Function<Long, String>) McpTokens::wrongAudienceToken),
                Arguments.of("over-ttl token", (Function<Long, String>) McpTokens::overTtlToken));
    }

    static Stream<Arguments> unknownCallerSubjects() {
        return Stream.of(
                Arguments.of("a number matching no user row", 900_100_001L),
                Arguments.of("a Telegram identifier minted before the change", 900_100_002L));
    }
}
