package bot.finance.ai.application.port;

import bot.finance.ai.domain.value.TurnEnd;

public interface TurnEndPort {

    void report(TurnEnd end);
}
