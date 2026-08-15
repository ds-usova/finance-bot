package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.domain.exception.InvalidValueException;

public class ExtractIntentsUseCase implements ExtractIntentsPort {

    private final ExpenseRecordingPort expenseRecordingPort;
    private final MessageStorePort messageStorePort;
    private final Logger log;

    public ExtractIntentsUseCase(
            ExpenseRecordingPort expenseRecordingPort, MessageStorePort messageStorePort, LoggerFactory loggerFactory) {
        this.expenseRecordingPort = expenseRecordingPort;
        this.messageStorePort = messageStorePort;
        this.log = loggerFactory.getLogger(ExtractIntentsUseCase.class);
    }

    @Override
    public void extractIntents(ExtractIntentsCommand command) {
        if (command == null) {
            throw new InvalidValueException("Command must not be null");
        }

        // TODO: register the message under the command's identity, when present, before the expense is recorded;
        // a MessageStoreFailedException is logged once at WARN naming the user id and message id (never the text)
        // and swallowed, so the turn goes on and the INFO line below is still logged

        expenseRecordingPort.record(
                command.text(),
                command.categoryGroupings(),
                command.catchAllGrouping(),
                command.defaultCurrency(),
                command.currentDate());

        log.info(
                "Acted on turn with {} groupings offered",
                command.categoryGroupings().size());
    }
}
