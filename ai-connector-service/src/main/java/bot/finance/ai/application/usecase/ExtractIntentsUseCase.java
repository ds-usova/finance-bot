package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.exception.InvalidValueException;

public class ExtractIntentsUseCase implements ExtractIntentsPort {

    private final ExpenseRecordingPort expenseRecordingPort;
    private final Logger log;

    public ExtractIntentsUseCase(ExpenseRecordingPort expenseRecordingPort, LoggerFactory loggerFactory) {
        this.expenseRecordingPort = expenseRecordingPort;
        this.log = loggerFactory.getLogger(ExtractIntentsUseCase.class);
    }

    @Override
    public void extractIntents(ExtractIntentsCommand command) {
        if (command == null) {
            throw new InvalidValueException("Command must not be null");
        }

        // TODO: turn command.knownCategories() into "Grouping > Category" labels in the command's order, call
        // expenseRecordingPort.record(command.text(), labels, command.defaultCurrency()), and log at INFO that
        // the turn was acted on, naming how many categories were offered and carrying nothing from the message
        // text.
    }

}
