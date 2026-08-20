package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.RecallExamplesCommand;
import bot.finance.ai.domain.value.MessageExample;
import java.util.List;
import java.util.Optional;

public interface RecallExamplesPort {

    Optional<List<MessageExample>> recall(RecallExamplesCommand command);
}
