package bot.finance.ai.common;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Stubs the ledger's MCP endpoint ({@code /mcp}) that {@code McpExpenseProposalAdapter} calls,
 * registered through {@link WireMockSupport#SERVER}, never the static DSL.
 *
 * <p>A tool call is never the first thing on the wire: the MCP lifecycle makes every client send
 * {@code initialize} and then the {@code notifications/initialized} notification before anything else, so each
 * scenario below stubs that handshake too. A response is correlated by its JSON-RPC id, which the client
 * generates, so every stub echoes the id off the request rather than answering with a fixed one.
 */
public final class McpLedgerStubs {

    public static final String MCP_PATH = "/mcp";

    private static final String ECHOED_ID = "{{jsonPath request.body '$.id'}}";

    private McpLedgerStubs() {}

    /**
     * The ledger accepts a {@code create_expense_proposal} tool call and answers a stored proposal.
     */
    public static void stubCreateExpenseProposalAccepted() {
        stubHandshake();
        stubToolCall(
                """
                {
                  "jsonrpc": "2.0",
                  "id": "%s",
                  "result": {
                    "content": [{"type": "text", "text": "proposal stored"}]
                  }
                }
                """);
    }

    /**
     * The ledger answers a {@code create_expense_proposal} tool call with a tool result flagged {@code isError}
     * — a refused proposal.
     */
    public static void stubCreateExpenseProposalRefused() {
        stubHandshake();
        stubToolCall(
                """
                {
                  "jsonrpc": "2.0",
                  "id": "%s",
                  "result": {
                    "isError": true,
                    "content": [{"type": "text", "text": "several categories named Travel exist"}]
                  }
                }
                """);
    }

    /**
     * The ledger's {@code /mcp} endpoint fails the transport outright — a connection the adapter cannot reach.
     * Registered without a body matcher, so it takes down the handshake as well as the tool call.
     */
    public static void stubCreateExpenseProposalTransportFailure() {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(MCP_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    /**
     * Answers the two lifecycle messages the client sends before any tool call: {@code initialize}, whose result
     * names a protocol version the client itself supports, and the {@code notifications/initialized}
     * notification, which carries no id and expects no body.
     */
    private static void stubHandshake() {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(MCP_PATH))
                .withRequestBody(matchingJsonPath("$.method", equalTo("initialize")))
                .willReturn(jsonRpc(
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

    private static void stubToolCall(String bodyTemplate) {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(MCP_PATH))
                .withRequestBody(matchingJsonPath("$.params.name", equalTo("create_expense_proposal")))
                .willReturn(jsonRpc(bodyTemplate)));
    }

    private static ResponseDefinitionBuilder jsonRpc(String bodyTemplate) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(bodyTemplate.formatted(ECHOED_ID))
                .withTransformers("response-template");
    }
}
