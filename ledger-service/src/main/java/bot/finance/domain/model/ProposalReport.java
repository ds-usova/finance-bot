package bot.finance.domain.model;

import bot.finance.domain.exception.InvalidProposalReportException;
import bot.finance.domain.value.IncomingMessageId;

public final class ProposalReport extends Entity {

    private final long userId;
    private final IncomingMessageId incomingMessageId;
    private final String conversationId;
    private final String sentMessageId;

    private ProposalReport(
            Long id, long userId, IncomingMessageId incomingMessageId, String conversationId, String sentMessageId) {
        super(id);
        if (userId <= 0) {
            throw new InvalidProposalReportException("user id must be positive");
        }
        if (incomingMessageId == null) {
            throw new InvalidProposalReportException("incoming message id must be present");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new InvalidProposalReportException("conversation id must be present");
        }
        if (sentMessageId == null || sentMessageId.isBlank()) {
            throw new InvalidProposalReportException("sent message id must be present");
        }
        this.userId = userId;
        this.incomingMessageId = incomingMessageId;
        this.conversationId = conversationId;
        this.sentMessageId = sentMessageId;
    }

    /** A report about to be recorded, for the location a delivery answered. */
    public static ProposalReport newProposalReport(
            long userId, IncomingMessageId incomingMessageId, String conversationId, String sentMessageId) {
        return new ProposalReport(null, userId, incomingMessageId, conversationId, sentMessageId);
    }

    public static ProposalReport stored(
            long id, long userId, IncomingMessageId incomingMessageId, String conversationId, String sentMessageId) {
        return new ProposalReport(id, userId, incomingMessageId, conversationId, sentMessageId);
    }

    public long userId() {
        return userId;
    }

    public IncomingMessageId incomingMessageId() {
        return incomingMessageId;
    }

    public String conversationId() {
        return conversationId;
    }

    public String sentMessageId() {
        return sentMessageId;
    }
}
