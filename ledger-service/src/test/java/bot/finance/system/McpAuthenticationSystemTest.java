package bot.finance.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.common.AbstractSystemTest;
import bot.finance.common.ExpenseProposalRowUtils;
import bot.finance.common.McpRequests;
import bot.finance.common.McpTokens;
import bot.finance.common.UserRowUtils;
import bot.finance.common.containers.GrpcStubServer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.Map;
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
        @DisplayName(
                "when tools/list is posted with a valid token - then 200 lists the tool with exactly its own arguments and required arguments, and no identity argument among them")
        void whenToolsListIsPostedWithValidToken_thenEachPublishedToolIsListedWithItsArgumentsAndNoIdentityArgument(
                String toolName, List<String> expectedArguments, List<String> expectedRequiredArguments) {
            String externalId = "mcp-auth-tools-list-user-" + toolName;
            UserRowUtils.storedUserId(userEntityRepository, externalId);
            String token = McpTokens.tokenFor(accessTokenMinter, externalId);

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
        @DisplayName(
                "when tools/list is posted with a valid token - then create_expense_proposal publishes amount as a string, so a number is never accepted for it")
        void whenToolsListIsPostedWithValidToken_thenAmountIsPublishedAsAString() {
            String externalId = "mcp-auth-amount-type-user";
            UserRowUtils.storedUserId(userEntityRepository, externalId);
            String token = McpTokens.tokenFor(accessTokenMinter, externalId);

            Response response = postMcp(token, McpRequests.toolsList());

            response.then().statusCode(200);
            assertThat(response.jsonPath()
                            .getMap("result.tools.find { it.name == 'create_expense_proposal' }"
                                    + ".inputSchema.properties.amount"))
                    .as("amount's published type")
                    .containsEntry("type", "string");
        }
    }

    @Nested
    @DisplayName("unhappy path")
    class UnhappyPath {

        @ParameterizedTest(name = "{0}")
        @MethodSource("bot.finance.system.McpAuthenticationSystemTest#rejectedTokens")
        @DisplayName(
                "when tools/call create_expense_proposal is posted with a rejected token - then 401 with no tool result and no expense_proposal row written")
        void whenToolsCallIsPostedWithRejectedToken_thenUnauthorizedWithNoToolResultAndNoRowWritten(
                String scenario, String token, String externalId) {
            long userId = UserRowUtils.storedUserId(userEntityRepository, externalId);

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

        @Test
        @DisplayName("when GET /actuator/health is requested with no token - then 200")
        void whenActuatorHealthIsRequestedWithNoToken_thenOk() {
            Response response = RestAssured.given().when().get("/actuator/health");
            logResponse(response);

            response.then().statusCode(200);
        }
    }

    static Stream<Arguments> publishedTools() {
        return Stream.of(
                Arguments.of(
                        "create_expense_proposal",
                        List.of("category", "parentCategory", "description", "merchant", "amount", "currencyCode"),
                        List.of("amount", "parentCategory")),
                Arguments.of("list_categories", List.of("parentCategory"), List.of("parentCategory")));
    }

    static Stream<Arguments> rejectedTokens() {
        String noTokenUser = "mcp-auth-no-token-user";
        String expiredTokenUser = "mcp-auth-expired-token-user";
        String wrongAudienceTokenUser = "mcp-auth-wrong-audience-token-user";
        String overTtlTokenUser = "mcp-auth-over-ttl-token-user";
        return Stream.of(
                Arguments.of("no token", null, noTokenUser),
                Arguments.of("expired token", McpTokens.expiredToken(expiredTokenUser), expiredTokenUser),
                Arguments.of(
                        "wrong-audience token",
                        McpTokens.wrongAudienceToken(wrongAudienceTokenUser),
                        wrongAudienceTokenUser),
                Arguments.of("over-ttl token", McpTokens.overTtlToken(overTtlTokenUser), overTtlTokenUser));
    }
}
