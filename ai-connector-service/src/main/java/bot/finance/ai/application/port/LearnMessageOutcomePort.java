package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.application.dto.LearnOutcome;

public interface LearnMessageOutcomePort {

    LearnOutcome learn(LearnMessageOutcomeCommand command);
}
