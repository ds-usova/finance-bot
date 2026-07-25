package bot.finance.common;

/**
 * Telegram Bot API JSON bodies for tests, in the two shapes the Bot API actually uses — which must never be
 * conflated:
 *
 * <ul>
 *   <li><b>bare {@code Update} objects</b> ({@link #textMessageUpdate}, {@link #voiceMessageUpdate},
 *       {@link #textMessageUpdateWithoutChat}, {@link #callbackQueryUpdate}) — what
 *       {@code BotUtils.parseUpdate(String)} deserializes, so these are the inputs for tests that call the
 *       mapper directly;</li>
 *   <li><b>{@code getUpdates} envelopes</b> ({@link #updatesResponse}, {@link #noUpdates}, {@link #error}) —
 *       {@code {"ok":…,"result":[…]}}, what the stub server serves. {@link #updatesResponse} wraps any number of
 *       the bare bodies above, which is what makes a multi-update batch expressible.</li>
 * </ul>
 *
 * Feeding an envelope to {@code parseUpdate} yields an {@code Update} with every field null, so pick the shape
 * by consumer, not by convenience.
 *
 * <p>These are Java text blocks rather than {@code src/test/resources} files loaded through {@link JsonUtils} —
 * a deliberate exception to that convention, because every body is parameterized by {@code updateId},
 * {@code chatId} or {@code text} and {@link JsonUtils#readJsonResourceAsString} performs no substitution. Move
 * a body to a resource file if it outgrows roughly fifteen lines.
 */
public final class TelegramFixtures {

    private static final int MESSAGE_ID = 1;
    private static final int MESSAGE_DATE = 1700000000;

    private TelegramFixtures() {
    }

    /**
     * A bare {@code Update} carrying a text message in a private chat.
     */
    public static String textMessageUpdate(int updateId, long chatId, String text) {
        return """
                {
                  "update_id": %d,
                  "message": {
                    "message_id": %d,
                    "date": %d,
                    "chat": { "id": %d, "type": "private" },
                    "text": "%s"
                  }
                }""".formatted(updateId, MESSAGE_ID, MESSAGE_DATE, chatId, escaped(text));
    }

    /**
     * A bare {@code Update} carrying a voice message — a message with a payload but no text.
     */
    public static String voiceMessageUpdate(int updateId, long chatId) {
        return """
                {
                  "update_id": %d,
                  "message": {
                    "message_id": %d,
                    "date": %d,
                    "chat": { "id": %d, "type": "private" },
                    "voice": {
                      "file_id": "voice-file-id",
                      "file_unique_id": "voice-file-unique-id",
                      "duration": 3,
                      "mime_type": "audio/ogg"
                    }
                  }
                }""".formatted(updateId, MESSAGE_ID, MESSAGE_DATE, chatId);
    }

    /**
     * A bare {@code Update} whose message carries text but no {@code chat}, so there is no conversation to
     * render an id from.
     */
    public static String textMessageUpdateWithoutChat(int updateId, String text) {
        return """
                {
                  "update_id": %d,
                  "message": {
                    "message_id": %d,
                    "date": %d,
                    "text": "%s"
                  }
                }""".formatted(updateId, MESSAGE_ID, MESSAGE_DATE, escaped(text));
    }

    /**
     * A bare {@code Update} carrying no {@code message} at all.
     */
    public static String callbackQueryUpdate(int updateId) {
        return """
                {
                  "update_id": %d,
                  "callback_query": {
                    "id": "callback-query-id",
                    "from": { "id": 555, "is_bot": false, "first_name": "Tester" },
                    "chat_instance": "callback-chat-instance",
                    "data": "noop"
                  }
                }""".formatted(updateId);
    }

    /**
     * A successful {@code getUpdates} envelope wrapping the given bare {@code Update} bodies, in order.
     */
    public static String updatesResponse(String... bareUpdateJson) {
        String updates = String.join(",\n", bareUpdateJson);
        return """
                {
                  "ok": true,
                  "result": [
                %s
                  ]
                }""".formatted(updates);
    }

    /**
     * A successful {@code getUpdates} envelope carrying an empty batch.
     */
    public static String noUpdates() {
        return """
                {
                  "ok": true,
                  "result": []
                }""";
    }

    /**
     * A failed {@code getUpdates} envelope — {@code ok:false} with an error code, e.g. 429 for rate limiting.
     */
    public static String error(int errorCode, String description) {
        return """
                {
                  "ok": false,
                  "error_code": %d,
                  "description": "%s"
                }""".formatted(errorCode, escaped(description));
    }

    private static String escaped(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
