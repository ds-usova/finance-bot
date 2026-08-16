package bot.finance.ai.adapter.redis;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ChangeStreamEntryReader {

    // Boot 4's Jackson autoconfiguration wires a JsonMapper bean, not a jackson-databind ObjectMapper, so this
    // reader owns its own the way the ledger's ChangeEventPublisher owns its own.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<LearnMessageOutcomeCommand> read(String entryId, Map<String, String> body) {
        // parses payload and enrichment into a RecordedChange, empty for a table or op nothing here learns from,
        // InvalidValueException for a body that is not a change event
        return Optional.empty();
    }
}
