package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.RecallExamplesCommand;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.application.port.RecallExamplesPort;
import bot.finance.ai.application.port.TurnMeters;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.value.MessageExample;
import bot.finance.ai.domain.value.MessageIdentity;
import java.util.List;
import java.util.Optional;

public class ExtractIntentsUseCase implements ExtractIntentsPort {

    private final ExtractionTurn extractionTurn;
    private final TurnMeters turnMeters;
    private final MessageStorePort messageStorePort;
    private final RecallExamplesPort recallExamplesPort;
    private final Logger log;

    public ExtractIntentsUseCase(
            ExtractionTurn extractionTurn,
            TurnMeters turnMeters,
            MessageStorePort messageStorePort,
            RecallExamplesPort recallExamplesPort,
            LoggerFactory loggerFactory) {
        this.extractionTurn = extractionTurn;
        this.turnMeters = turnMeters;
        this.messageStorePort = messageStorePort;
        this.recallExamplesPort = recallExamplesPort;
        this.log = loggerFactory.getLogger(ExtractIntentsUseCase.class);
    }

    @Override
    public void extractIntents(ExtractIntentsCommand command) {
        if (command == null) {
            throw new InvalidValueException("Command must not be null");
        }

        Optional<List<MessageExample>> examples = Optional.empty();
        Optional<MessageIdentity> identity = command.messageIdentity();
        if (identity.isPresent()) {
            registerMessage(identity.get(), command.text());
            examples = recallExamplesPort.recall(new RecallExamplesCommand(identity.get(), command.text()));
        }

        try {
            extractionTurn.run(command, examples);
        } catch (ExpenseRecordingFailedException e) {
            turnMeters.recordTurn(extractionTurn.mode(), false);
            throw e;
        }
        turnMeters.recordTurn(extractionTurn.mode(), true);

        log.info(
                "Acted on turn with {} groupings offered",
                command.categoryGroupings().size());
    }

    private void registerMessage(MessageIdentity identity, String text) {
        try {
            messageStorePort.register(identity, text);
        } catch (MessageStoreFailedException e) {
            log.warn("Failed to register message {} for user {}", identity.incomingMessageId(), identity.userId());
        }
    }
}
