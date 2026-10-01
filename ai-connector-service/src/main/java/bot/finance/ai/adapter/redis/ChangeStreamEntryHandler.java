package bot.finance.ai.adapter.redis;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.application.dto.LearnOutcome;
import bot.finance.ai.application.port.LearnMessageOutcomePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class ChangeStreamEntryHandler {

    private final ChangeStreamEntryReader reader;
    private final LearnMessageOutcomePort learnMessageOutcomePort;
    private final ChangeStreamProperties properties;
    private final Logger log;

    public ChangeStreamEntryHandler(
            ChangeStreamEntryReader reader,
            LearnMessageOutcomePort learnMessageOutcomePort,
            ChangeStreamProperties properties,
            LoggerFactory loggerFactory) {
        this.reader = reader;
        this.learnMessageOutcomePort = learnMessageOutcomePort;
        this.properties = properties;
        this.log = loggerFactory.getLogger(ChangeStreamEntryHandler.class);
    }

    public boolean handle(
            StreamOperations<String, String, String> streamOperations, MapRecord<String, String, String> entry) {
        String entryId = entry.getId().getValue();

        Optional<LearnMessageOutcomeCommand> command;
        try {
            command = reader.read(entryId, entry.getValue());
        } catch (InvalidValueException e) {
            log.warn(
                    "Change-stream entry {} could not be read, acknowledging without applying: {}",
                    entryId,
                    e.getMessage());
            acknowledge(streamOperations, entryId);
            return false;
        }

        if (command.isEmpty()) {
            acknowledge(streamOperations, entryId);
            return false;
        }

        return offer(streamOperations, entryId, command.get());
    }

    private boolean offer(
            StreamOperations<String, String, String> streamOperations,
            String entryId,
            LearnMessageOutcomeCommand command) {
        LearnOutcome outcome;
        try {
            outcome = learnMessageOutcomePort.learn(command);
        } catch (RuntimeException e) {
            log.error(
                    "Learning the outcome of change-stream entry {} failed, leaving it pending: {}",
                    entryId,
                    e.getMessage());
            return true;
        }

        return switch (outcome) {
            case APPLIED, DROPPED -> {
                acknowledge(streamOperations, entryId);
                yield false;
            }
            case RETRY_LATER -> true;
        };
    }

    private void acknowledge(StreamOperations<String, String, String> streamOperations, String entryId) {
        streamOperations.acknowledge(properties.key(), ChangeStreamProperties.GROUP, entryId);
    }
}
