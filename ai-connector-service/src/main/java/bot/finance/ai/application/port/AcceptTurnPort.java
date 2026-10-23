package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.AcceptTurnCommand;
import bot.finance.ai.application.dto.TurnAcceptance;

public interface AcceptTurnPort {

    TurnAcceptance accept(AcceptTurnCommand command);
}
