package bot.finance.application.port;

import bot.finance.application.dto.EndTurnCommand;
import bot.finance.domain.exception.InvalidTurnEndException;
import bot.finance.domain.exception.PersistenceFailedException;

public interface EndTurnPort {

    /**
     * @throws InvalidTurnEndException if the command is absent
     * @throws PersistenceFailedException if the turn cannot be read or settled
     */
    void end(EndTurnCommand command);
}
