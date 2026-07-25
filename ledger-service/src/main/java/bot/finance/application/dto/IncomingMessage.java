package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidIncomingMessageException;

/**
 * {@code conversationId} is whatever the delivering adapter's transport identifies a conversation by, rendered
 * as a string.
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
