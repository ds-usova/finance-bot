package bot.finance.ai.application.usecase;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.domain.value.ExtractionMode;
import bot.finance.ai.domain.value.MessageExample;
import java.util.List;
import java.util.Optional;

public interface ExtractionTurn {

    ExtractionMode mode();

    void run(ExtractIntentsCommand command, Optional<List<MessageExample>> examples);
}
