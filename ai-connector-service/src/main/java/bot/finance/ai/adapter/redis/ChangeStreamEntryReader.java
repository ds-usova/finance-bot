package bot.finance.ai.adapter.redis;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ChangeStreamEntryReader {

    public Optional<LearnMessageOutcomeCommand> read(String entryId, Map<String, String> body) {
        // Intent: map ProposalCreated/ProposalRefiled to PROPOSED, ProposalDiscarded to
        // DISCARDED, and ProposalAccepted/ExpenseRecorded/ExpenseRefiled to ACCEPTED - never reading the
        // body's own "status" field; read the event's payload (userId, incomingMessageId, expenseId,
        // description, merchant, amount, currencyCode, category, grouping) into a SpendingRow; parse the
        // entry id's "<ms>-<seq>" shape into a StreamPosition; answer empty for any other type, and throw
        // InvalidValueException for a body with no payload, no type, unparsable JSON, or a non-positive
        // expenseId/userId, or for an entry id that is not "<ms>-<seq>" with a positive ms.
        return Optional.empty();
    }
}
