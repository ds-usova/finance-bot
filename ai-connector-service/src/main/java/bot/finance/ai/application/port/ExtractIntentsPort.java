package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.IntentExtractionCommand;
import bot.finance.ai.domain.value.Intent;

import java.util.List;

public interface ExtractIntentsPort {

    List<Intent> extractIntents(IntentExtractionCommand command);

}
