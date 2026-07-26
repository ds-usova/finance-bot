package bot.finance.ai.common;

import java.util.List;

/**
 * Builders for OpenAI chat-completions response bodies.
 *
 * <p>Spring AI's structured output parses {@code choices[0].message.content} as a JSON string, not the
 * response body directly, so {@link #chatCompletionResponse(String)} embeds its argument there rather than
 * returning it verbatim. {@link #extractedIntentsJson(String...)} builds that argument — the
 * {@code {"intents":[…]}} wrapper {@code ExtractedIntents} deserializes — from zero or more entries built with
 * {@link #intentEntry()}.
 */
public final class ChatCompletionFixtures {

    private ChatCompletionFixtures() {
    }

    public static IntentEntryBuilder intentEntry() {
        return new IntentEntryBuilder();
    }

    public static String extractedIntentsJson(String... entries) {
        return "{\"intents\":[" + String.join(",", entries) + "]}";
    }

    public static String extractedIntentsJson(List<String> entries) {
        return extractedIntentsJson(entries.toArray(new String[0]));
    }

    public static String chatCompletionResponse(String extractedJson) {
        String content = extractedJson.replace("\\", "\\\\").replace("\"", "\\\"");
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

    /**
     * Builds one {@code ExtractedIntent} JSON fragment. Every field defaults to absent, mirroring a model
     * answer that leaves what it is unsure of out entirely rather than sending an explicit null.
     */
    public static final class IntentEntryBuilder {

        private String target;
        private String operation;
        private String categoryName;
        private String newCategoryName;
        private String amount;
        private String currency;
        private String description;

        private IntentEntryBuilder() {
        }

        public IntentEntryBuilder target(String target) {
            this.target = target;
            return this;
        }

        public IntentEntryBuilder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public IntentEntryBuilder categoryName(String categoryName) {
            this.categoryName = categoryName;
            return this;
        }

        public IntentEntryBuilder newCategoryName(String newCategoryName) {
            this.newCategoryName = newCategoryName;
            return this;
        }

        public IntentEntryBuilder amount(String amount) {
            this.amount = amount;
            return this;
        }

        public IntentEntryBuilder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public IntentEntryBuilder description(String description) {
            this.description = description;
            return this;
        }

        public String build() {
            StringBuilder json = new StringBuilder("{");
            appendField(json, "target", target);
            appendField(json, "operation", operation);
            appendField(json, "categoryName", categoryName);
            appendField(json, "newCategoryName", newCategoryName);
            appendField(json, "amount", amount);
            appendField(json, "currency", currency);
            appendField(json, "description", description);
            if (!json.isEmpty() && json.charAt(json.length() - 1) == ',') {
                json.setLength(json.length() - 1);
            }
            return json.append("}").toString();
        }

        private static void appendField(StringBuilder json, String name, String value) {
            if (value != null) {
                json.append('"').append(name).append("\":\"").append(value).append("\",");
            }
        }

    }

}
