package bot.finance.adapter.telegram;

import bot.finance.application.dto.ProposalResolution;
import bot.finance.domain.value.MessageReference;
import java.util.Optional;
import java.util.UUID;

public final class ProposalCallbackData {

    private ProposalCallbackData() {}

    public record ParsedCallback(ProposalResolution resolution, MessageReference reference) {}

    public static String render(ProposalResolution resolution, MessageReference reference) {
        String verb = resolution == ProposalResolution.ACCEPT ? "accept" : "discard";
        return verb + ":" + reference.value();
    }

    public static Optional<ParsedCallback> parse(String data) {
        if (data == null || data.isBlank()) {
            return Optional.empty();
        }
        int colonIndex = data.indexOf(':');
        if (colonIndex < 0) {
            return Optional.empty();
        }
        String verb = data.substring(0, colonIndex);
        String uuid = data.substring(colonIndex + 1);
        if (uuid.isBlank()) {
            return Optional.empty();
        }
        ProposalResolution resolution;
        if ("accept".equals(verb)) {
            resolution = ProposalResolution.ACCEPT;
        } else if ("discard".equals(verb)) {
            resolution = ProposalResolution.DISCARD;
        } else {
            return Optional.empty();
        }
        try {
            UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.of(new ParsedCallback(resolution, MessageReference.of(uuid)));
    }
}
