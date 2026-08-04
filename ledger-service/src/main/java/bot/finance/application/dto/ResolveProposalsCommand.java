package bot.finance.application.dto;

import bot.finance.domain.value.MessageReference;

public record ResolveProposalsCommand(
        String userExternalId,
        String conversationId,
        String reportMessageId,
        String interactionId,
        MessageReference reference,
        ProposalResolution resolution) {

    public ResolveProposalsCommand {
        // TODO RU01: reject a blank string, an absent reference and an absent resolution with
        // InvalidIncomingMessageException
    }
}
