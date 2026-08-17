package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.application.port.RecallExamplesPort;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.value.MessageIdentity;
import java.util.Optional;

public class ExtractIntentsUseCase implements ExtractIntentsPort {

    private final ExpenseRecordingPort expenseRecordingPort;
    private final MessageStorePort messageStorePort;
    private final RecallExamplesPort recallExamplesPort;
    private final Logger log;

    public ExtractIntentsUseCase(
            ExpenseRecordingPort expenseRecordingPort,
            MessageStorePort messageStorePort,
            RecallExamplesPort recallExamplesPort,
            LoggerFactory loggerFactory) {
        this.expenseRecordingPort = expenseRecordingPort;
        this.messageStorePort = messageStorePort;
        this.recallExamplesPort = recallExamplesPort;
        this.log = loggerFactory.getLogger(ExtractIntentsUseCase.class);
    }

    @Override
    public void extractIntents(ExtractIntentsCommand command) {
        if (command == null) {
            throw new InvalidValueException("Command must not be null");
        }

        command.messageIdentity().ifPresent(identity -> registerMessage(identity, command.text()));

        // recalls the person's closest earlier messages once the turn's own has been registered
        expenseRecordingPort.record(
                command.text(),
                command.categoryGroupings(),
                command.catchAllGrouping(),
                command.defaultCurrency(),
                command.currentDate(),
                Optional.empty());

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
