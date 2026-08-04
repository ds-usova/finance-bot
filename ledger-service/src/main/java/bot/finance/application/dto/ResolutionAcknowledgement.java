package bot.finance.application.dto;

public record ResolutionAcknowledgement(
        String conversationId, String reportMessageId, String interactionId, ResolutionOutcome outcome, int count) {}
