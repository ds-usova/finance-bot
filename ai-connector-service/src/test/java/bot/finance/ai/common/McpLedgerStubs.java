package bot.finance.ai.common;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Stubs the ledger's MCP endpoint ({@code /mcp}) that {@code McpExpenseProposalAdapter} calls,
 * registered through {@link WireMockSupport#SERVER}, never the static DSL.
 */
public final class McpLedgerStubs {

    public static final String MCP_PATH = "/mcp";

    private McpLedgerStubs() {}

    /**
     * The ledger accepts a {@code create_expense_proposal} tool call and answers a stored proposal.
     */
    public static void stubCreateExpenseProposalAccepted() {
        // Accepts a JSON-RPC POST to /mcp whose method is tools/call and whose tool name is
        // create_expense_proposal, and answers a JSON-RPC result carrying a stored proposal.
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(MCP_PATH))
                .withRequestBody(matchingJsonPath("$.params.name", equalTo("create_expense_proposal")))
                .willReturn(okJson(
                        """
                        {
                          "jsonrpc": "2.0",
                          "id": 2,
                          "result": {
                            "content": [{"type": "text", "text": "proposal stored"}]
                          }
                        }
                        """)));
    }

    /**
     * The ledger answers a {@code create_expense_proposal} tool call with a tool result flagged {@code isError}
     * — a refused proposal.
     */
    public static void stubCreateExpenseProposalRefused() {
        WireMockSupport.SERVER.stubFor(post(urlPathEqualTo(MCP_PATH))
                .withRequestBody(matchingJsonPath("$.params.name", equalTo("create_expense_proposal")))
                .willReturn(okJson(
                        """
                        {
                          "jsonrpc": "2.0",
                          "id": 2,
                          "result": {
                            "isError": true,
                            "content": [{"type": "text", "text": "several categories named Travel exist"}]
                          }
                        }
                        """)));
    }

    /**
     * The ledger's {@code /mcp} endpoint fails the transport outright — a connection the adapter cannot reach.
     */
    public static void stubCreateExpenseProposalTransportFailure() {
        WireMockSupport.SERVER.stubFor(
                post(urlPathEqualTo(MCP_PATH)).willReturn(aResponse().withFault(
                        com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));
    }

}
