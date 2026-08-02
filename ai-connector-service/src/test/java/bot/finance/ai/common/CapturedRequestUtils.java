package bot.finance.ai.common;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;

/**
 * Reads back the requests {@link WireMockSupport#SERVER} recorded, so a test asserts on what actually went to the
 * provider and to the ledger. Every body is parsed with an unchecked failure, keeping the checked
 * {@code JsonProcessingException} out of test signatures and lambdas.
 */
public final class CapturedRequestUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CREATE_EXPENSE_PROPOSAL = "create_expense_proposal";

    private CapturedRequestUtils() {}

    public static List<LoggedRequest> chatCompletionRequests() {
        return capturedRequests(WireMockStubs.CHAT_COMPLETIONS_PATH);
    }

    public static List<LoggedRequest> mcpRequests() {
        return capturedRequests(McpLedgerStubs.MCP_PATH);
    }

    /**
     * Among the MCP requests recorded, those invoking {@code create_expense_proposal} — as opposed to the
     * handshake and {@code tools/list} requests a session also performs.
     */
    public static List<LoggedRequest> toolCallRequests() {
        return mcpRequests().stream()
                .filter(request -> CREATE_EXPENSE_PROPOSAL.equals(
                        body(request).at("/params/name").asText()))
                .toList();
    }

    public static JsonNode toolCallArguments(LoggedRequest toolCallRequest) {
        return body(toolCallRequest).at("/params/arguments");
    }

    public static JsonNode body(LoggedRequest request) {
        try {
            return MAPPER.readTree(request.getBodyAsString());
        } catch (JsonProcessingException e) {
            throw new AssertionError("captured request body is not JSON: " + request.getBodyAsString(), e);
        }
    }

    /**
     * The content of the first message on a chat-completion request body carrying {@code role}.
     */
    public static String messageContent(JsonNode requestBody, String role) {
        for (JsonNode message : requestBody.get("messages")) {
            if (role.equals(message.path("role").asText())) {
                return message.get("content").asText();
            }
        }
        throw new AssertionError("no message with role " + role + " in " + requestBody);
    }

    private static List<LoggedRequest> capturedRequests(String path) {
        return WireMockSupport.SERVER.findAll(postRequestedFor(urlPathEqualTo(path)));
    }
}
