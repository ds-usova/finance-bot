package bot.finance.application.port;

import bot.finance.application.dto.ReadSessionCommand;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;

public interface ReadSessionPort {

    /**
     * @throws InvalidUserException if the command is absent
     * @throws EntityNotFoundException if the command's identity names no stored user
     * @throws PersistenceFailedException if the lookup fails
     */
    User read(ReadSessionCommand command);
}
