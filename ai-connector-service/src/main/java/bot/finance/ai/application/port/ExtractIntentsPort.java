package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.domain.value.Intent;

import java.util.List;

public interface ExtractIntentsPort {

    List<Intent> extractIntents(ExtractIntentsCommand command);

}
