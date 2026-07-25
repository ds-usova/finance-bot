package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidIncomingMessageException;

/**
 * The inbound-port command for a message that arrived from a conversation, in transport-agnostic terms: the
 * core never learns which messenger delivered it, so {@code conversationId} is a plain string an adapter
 * renders from whatever identifier its own transport uses.
 *
 * <p>This is the single place field-level validation happens — the compact constructor rejects an invalid
 * command, so one cannot be constructed anywhere in the system and no caller re-checks its fields.
 *
 * @param conversationId the conversation the message belongs to, as rendered by the delivering adapter
 * @param text           the message text
 */
public record IncomingMessage(String conversationId, String text) {

    public IncomingMessage {
        if (conversationId == null || conversationId.isBlank()) {
            throw new InvalidIncomingMessageException("incoming message has no conversation id");
        }
        if (text == null || text.isBlank()) {
            throw new InvalidIncomingMessageException("incoming message has no text");
        }
    }

}
