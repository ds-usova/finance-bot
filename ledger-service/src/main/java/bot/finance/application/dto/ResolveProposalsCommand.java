package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.value.IncomingMessageId;

public record ResolveProposalsCommand(
        String userExternalId,
        String conversationId,
        String reportMessageId,
        String interactionId,
        IncomingMessageId reference,
        ProposalResolution resolution) {

    public ResolveProposalsCommand {
        if (userExternalId == null || userExternalId.isBlank()) {
            throw new InvalidIncomingMessageException("resolve-proposals command has no user external id");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new InvalidIncomingMessageException("resolve-proposals command has no conversation id");
        }
        if (reportMessageId == null || reportMessageId.isBlank()) {
            throw new InvalidIncomingMessageException("resolve-proposals command has no report message id");
        }
        if (interactionId == null || interactionId.isBlank()) {
            throw new InvalidIncomingMessageException("resolve-proposals command has no interaction id");
        }
        if (reference == null) {
            throw new InvalidIncomingMessageException("resolve-proposals command has no reference");
        }
        if (resolution == null) {
            throw new InvalidIncomingMessageException("resolve-proposals command has no resolution");
        }
    }
}
