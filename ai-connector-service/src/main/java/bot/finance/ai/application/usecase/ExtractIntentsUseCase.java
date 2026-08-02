package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.KnownCategory;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.List;

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

        List<String> labels =
                command.knownCategories().stream().map(KnownCategory::label).toList();

        expenseRecordingPort.record(command.text(), labels, command.defaultCurrency());

        log.info("Acted on turn with {} known categories offered", labels.size());
    }
}
