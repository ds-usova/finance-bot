package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.domain.value.ExtractionMode;
import bot.finance.ai.domain.value.MessageExample;
import java.util.List;
import java.util.Optional;

public class ToolCallingTurn implements ExtractionTurn {

    private final ExpenseRecordingPort expenseRecordingPort;

    public ToolCallingTurn(ExpenseRecordingPort expenseRecordingPort) {
        this.expenseRecordingPort = expenseRecordingPort;
    }

    @Override
    public ExtractionMode mode() {
        return ExtractionMode.TOOLS;
    }

    @Override
    public void run(ExtractIntentsCommand command, Optional<List<MessageExample>> examples) {
        expenseRecordingPort.record(
                command.text(),
                command.categoryGroupings(),
                command.catchAllGrouping(),
                command.defaultCurrency(),
                command.currentDate(),
                examples);
    }
}
