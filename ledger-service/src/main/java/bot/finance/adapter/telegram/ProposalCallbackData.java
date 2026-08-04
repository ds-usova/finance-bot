package bot.finance.adapter.telegram;

import bot.finance.application.dto.ProposalResolution;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.value.MessageReference;
import java.util.Optional;

public final class ProposalCallbackData {

    private static final String ACCEPT_VERB = "accept";
    private static final String DISCARD_VERB = "discard";
    private static final char SEPARATOR = ':';

    private ProposalCallbackData() {}

    public record ParsedCallback(ProposalResolution resolution, MessageReference reference) {}

    public static String render(ProposalResolution resolution, MessageReference reference) {
        String verb =
                switch (resolution) {
                    case ACCEPT -> ACCEPT_VERB;
                    case DISCARD -> DISCARD_VERB;
                };
        return verb + SEPARATOR + reference.value();
    }

    public static Optional<ParsedCallback> parse(String data) {
        if (data == null) {
            return Optional.empty();
        }
        int separatorIndex = data.indexOf(SEPARATOR);
        if (separatorIndex < 0) {
            return Optional.empty();
        }
        Optional<ProposalResolution> resolution = resolutionOf(data.substring(0, separatorIndex));
        Optional<MessageReference> reference = referenceOf(data.substring(separatorIndex + 1));
        if (resolution.isEmpty() || reference.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ParsedCallback(resolution.get(), reference.get()));
    }

    private static Optional<ProposalResolution> resolutionOf(String verb) {
        return switch (verb) {
            case ACCEPT_VERB -> Optional.of(ProposalResolution.ACCEPT);
            case DISCARD_VERB -> Optional.of(ProposalResolution.DISCARD);
            default -> Optional.empty();
        };
    }

    private static Optional<MessageReference> referenceOf(String value) {
        try {
            return Optional.of(MessageReference.of(value));
        } catch (InvalidIncomingMessageException e) {
            return Optional.empty();
        }
    }
}
