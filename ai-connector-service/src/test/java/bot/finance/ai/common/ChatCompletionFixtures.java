package bot.finance.ai.common;

/**
 * Builders for whole OpenAI chat-completion response bodies. {@link WireMockStubs} serves what they build
 * verbatim, with no further wrapping.
 */
public final class ChatCompletionFixtures {

    private ChatCompletionFixtures() {
    }

    /**
     * One {@code create_expense_proposal} tool-call entry, its arguments a JSON object encoded as a string — the
     * shape the provider sends, and the shape {@code SyncMcpToolCallback} expects to deserialize.
     */
    public static String toolCall(String id, String argumentsJson) {
        return """
                {
                  "id": "%s",
                  "type": "function",
                  "function": {
                    "name": "create_expense_proposal",
                    "arguments": %s
                  }
                }
                """.formatted(id, quote(argumentsJson));
    }

    /**
     * A chat-completion response whose {@code message} carries one or more tool calls and no content — how the
     * model asks for a tool to run.
     */
    public static String toolCallResponse(String... toolCalls) {
        return """
                {
                  "id": "chatcmpl-fixture",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "gpt-4o-mini",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "tool_calls": [%s]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ]
                }"""
                .formatted(String.join(",", toolCalls));
    }

    /**
     * A chat-completion response whose {@code message} carries plain text and no tool call — how the model ends
     * a turn, whether it recorded something or the message named no spending.
     */
    public static String textResponse(String content) {
        return """
                {
                  "id": "chatcmpl-fixture",
                  "object": "chat.completion",
                  "created": 1700000000,
                  "model": "gpt-4o-mini",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "%s"
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }"""
                .formatted(content);
    }

    private static String quote(String json) {
        return "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

}
