package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidIncomingMessageException;

/**
 * {@code userExternalId} is who the ledger stores the turn under; {@code conversationId} is whatever the
 * delivering adapter's transport identifies a conversation by, rendered as a string; {@code inboundMessageId} is
 * the message this turn answers.
 */
public record HandleIncomingMessageCommand(
        String userExternalId, String conversationId, String inboundMessageId, String text) {

    public HandleIncomingMessageCommand {
        if (userExternalId == null || userExternalId.isBlank()) {
            throw new InvalidIncomingMessageException("incoming message has no user external id");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new InvalidIncomingMessageException("incoming message has no conversation id");
        }
        if (inboundMessageId == null || inboundMessageId.isBlank()) {
            throw new InvalidIncomingMessageException("incoming message has no inbound message id");
        }
        if (text == null || text.isBlank()) {
            throw new InvalidIncomingMessageException("incoming message has no text");
        }
    }
}
