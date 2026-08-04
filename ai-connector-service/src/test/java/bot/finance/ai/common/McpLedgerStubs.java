package bot.finance.ai.common;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Stubs the ledger's MCP endpoint ({@code /mcp}), registered through {@link WireMockSupport#SERVER}, never the
 * static DSL.
 *
 * <p>Every scenario stubs the {@code initialize} / {@code notifications/initialized} handshake and
 * {@code tools/list} as well as the tool call, whether or not that test expects them on the wire: the long-lived
 * client may already hold both from an earlier test's context, so a stub answering only the tool call passes or
 * fails by the order the suite happens to run in. A response is correlated by the JSON-RPC id the client
 * generates, so every stub echoes the id off the request rather than answering with a fixed one.
 */
public final class McpLedgerStubs {

    public static final String MCP_PATH = "/mcp";

    private static final String ECHOED_ID = "{{jsonPath request.body '$.id'}}";
    private static final String TOOL_CALL_SCENARIO = "create-expense-proposal-tool-call";
    private static final String REFUSED_ONCE = "refused-once";

    private McpLedgerStubs() {}

    /**
     * The ledger accepts every {@code create_expense_proposal} tool call and answers a stored proposal.
     */
    public static void stubCreateExpenseProposalAccepted() {
        stubHandshake();
        stubToolsList();
        stubToolCallResult(accepted());
    }

    /**
     * The ledger refuses the first {@code create_expense_proposal} tool call with a tool error result, then
     * accepts the corrected retry — the path the prompt's one-retry policy exercises.
     */
    public static void stubCreateExpenseProposalRefusedThenAccepted() {
        stubHandshake();
        stubToolsList();
        WireMockSupport.SERVER.stubFor(toolCallRequest()
                .inScenario(TOOL_CALL_SCENARIO)
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(jsonRpc(refused()))
                .willSetStateTo(REFUSED_ONCE));
        WireMockSupport.SERVER.stubFor(toolCallRequest()
                .inScenario(TOOL_CALL_SCENARIO)
                .whenScenarioStateIs(REFUSED_ONCE)
                .willReturn(jsonRpc(accepted())));
    }

    /**
     * The ledger refuses every {@code create_expense_proposal} tool call — the expense stays refused after the
     * prompt's one retry is spent.
     */
    public static void stubCreateExpenseProposalRefusedTwice() {
        stubHandshake();
        stubToolsList();
        stubToolCallResult(refused());
    }

    /**
     * The ledger answers a {@code list_categories} call with the given category names.
     */
    public static void stubListCategoriesAnswering(String grouping, List<String> categories) {
        WireMockSupport.SERVER.stubFor(
                listCategoriesRequest().willReturn(jsonRpc(listCategoriesResult(grouping, categories))));
    }

    /**
     * The ledger refuses a {@code list_categories} call with a tool error result.
     */
    public static void stubListCategoriesRefused() {
        WireMockSupport.SERVER.stubFor(listCategoriesRequest().willReturn(jsonRpc(refused())));
    }

    /**
     * The ledger's {@code /mcp} endpoint fails the transport outright — a connection the adapter cannot reach.
     * Registered without a body matcher, so it takes down the handshake as well as the tool call.
     */
    public static void stubCreateExpenseProposalTransportFailure() {
        WireMockSupport.SERVER.stubFor(
                post(urlPathEqualTo(MCP_PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    /**
     * Answers the two lifecycle messages the client sends before any tool call: {@code initialize}, whose result
     * names a protocol version the client itself supports, and the {@code notifications/initialized}
     * notification, which carries no id and expects no body.
     */
    private static void stubHandshake() {
        WireMockSupport.SERVER.stubFor(
                post(urlPathEqualTo(MCP_PATH))
                        .withRequestBody(matchingJsonPath("$.method", equalTo("initialize")))
                        .willReturn(
                                jsonRpc(
                                        """
                        {
                          "jsonrpc": "2.0",
                          "id": "%s",
                          "result": {
                            "protocolVersion": "2024-11-05",
                            "capabilities": {"tools": {}},
                            "serverInfo": {"name": "ledger-service", "version": "test"}
                          }
                        }
                        """)));

        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(MCP_PATH))
                .withRequestBody(matchingJsonPath("$.method", equalTo("notifications/initialized")))
                .willReturn(aResponse().withStatus(202)));
    }

    /**
     * Answers {@code tools/list} advertising {@code create_expense_proposal} with the six arguments
     * {@code CreateExpenseProposalMcpTool} declares.
     */
    private static void stubToolsList() {
        WireMockSupport.SERVER.stubFor(
                post(urlPathEqualTo(MCP_PATH))
                        .withRequestBody(matchingJsonPath("$.method", equalTo("tools/list")))
                        .willReturn(
                                jsonRpc(
                                        """
                        {
                          "jsonrpc": "2.0",
                          "id": "%s",
                          "result": {
                            "tools": [
                              {
                                "name": "create_expense_proposal",
                                "description": "Records a new expense proposal for the caller",
                                "inputSchema": {
                                  "type": "object",
                                  "properties": {
                                    "category": {"type": "string"},
                                    "grouping": {"type": "string"},
                                    "description": {"type": "string"},
                                    "merchant": {"type": "string"},
                                    "amount": {"type": "string"},
                                    "currencyCode": {"type": "string"}
                                  },
                                  "required": ["category", "grouping", "description", "amount", "currencyCode"]
                                }
                              },
                              {
                                "name": "list_categories",
                                "description": "Lists the categories filed under one of the caller's groupings.",
                                "inputSchema": {
                                  "type": "object",
                                  "properties": {
                                    "grouping": {"type": "string"}
                                  },
                                  "required": ["grouping"]
                                }
                              }
                            ]
                          }
                        }
                        """)));
    }

    private static void stubToolCallResult(String resultTemplate) {
        WireMockSupport.SERVER.stubFor(toolCallRequest().willReturn(jsonRpc(resultTemplate)));
    }

    private static MappingBuilder toolCallRequest() {
        return post(urlPathEqualTo(MCP_PATH))
                .withRequestBody(matchingJsonPath("$.params.name", equalTo("create_expense_proposal")));
    }

    private static MappingBuilder listCategoriesRequest() {
        return post(urlPathEqualTo(MCP_PATH))
                .withRequestBody(matchingJsonPath("$.params.name", equalTo("list_categories")));
    }

    private static String listCategoriesResult(String grouping, List<String> categories) {
        // The names sit inside "text", itself a JSON string carrying JSON, so their quotes are escaped twice.
        String categoriesJson =
                categories.stream().map(name -> "\\\"" + name + "\\\"").collect(Collectors.joining(","));
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "%%s",
                  "result": {
                    "content": [{"type": "text", "text": "{\\"grouping\\":\\"%s\\",\\"categories\\":[%s]}"}]
                  }
                }
                """
                .formatted(grouping, categoriesJson);
    }

    private static String accepted() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "%s",
                  "result": {
                    "content": [{"type": "text", "text": "proposal stored"}]
                  }
                }
                """;
    }

    private static String refused() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "%s",
                  "result": {
                    "isError": true,
                    "content": [{"type": "text", "text": "several categories named Travel exist"}]
                  }
                }
                """;
    }

    private static ResponseDefinitionBuilder jsonRpc(String bodyTemplate) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(bodyTemplate.formatted(ECHOED_ID))
                .withTransformers("response-template");
    }
}
