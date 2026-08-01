package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.ExtractIntentsCommand;

public interface ExtractIntentsPort {

    void extractIntents(ExtractIntentsCommand command);

}
