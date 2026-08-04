package bot.finance.adapter.telegram;

import bot.finance.application.dto.ProposalResolution;
import bot.finance.domain.value.MessageReference;
import java.util.Optional;

public final class ProposalCallbackData {

    private ProposalCallbackData() {}

    public record ParsedCallback(ProposalResolution resolution, MessageReference reference) {}

    // renders accept:<uuid> or discard:<uuid>
    public static String render(ProposalResolution resolution, MessageReference reference) {
        return "";
    }

    // splits the verb from the uuid and checks both itself before calling MessageReference.of, which throws
    public static Optional<ParsedCallback> parse(String data) {
        return Optional.empty();
    }
}
