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

    private static final String MCP_ACCEPT_HEADER = "application/json, text/event-stream";

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

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName(
                "when tools/list is posted with a valid token - then 200 lists create_expense_proposal with its six arguments and no identity argument")
        void whenToolsListIsPostedWithValidToken_thenCreateExpenseProposalToolIsListedWithSixArgumentsAndNoIdentityArgument() {
            String externalId = "mcp-auth-tools-list-user";
            UserRowUtils.storedUserId(userEntityRepository, externalId);
            String token = McpTokens.tokenFor(accessTokenMinter, externalId);

            Response response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .accept(MCP_ACCEPT_HEADER)
                    .header("Authorization", "Bearer " + token)
                    .body(McpRequests.toolsList())
                    .when()
                    .post("/mcp");
            logResponse(response);

            response.then().statusCode(200);

            List<Map<String, Object>> tools = response.jsonPath().getList("result.tools");
            Map<String, Object> createExpenseProposalTool = tools.stream()
                    .filter(tool -> "create_expense_proposal".equals(tool.get("name")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("create_expense_proposal was not listed: " + tools));

            @SuppressWarnings("unchecked")
            Map<String, Object> inputSchema = (Map<String, Object>) createExpenseProposalTool.get("inputSchema");
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            assertThat(properties.keySet())
                    .as("create_expense_proposal's argument names")
                    .containsExactlyInAnyOrder(
                            "category",
                            "parentCategory",
                            "description",
                            "merchant",
                            "amountMinorUnits",
                            "currencyCode");
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

            RequestSpecification request = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .accept(MCP_ACCEPT_HEADER)
                    .body(McpRequests.createExpenseProposal("Groceries", null, "lunch", "Cafe", 1000L, "EUR"));
            if (token != null) {
                request = request.header("Authorization", "Bearer " + token);
            }

            Response response = request.when().post("/mcp");
            logResponse(response);

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
