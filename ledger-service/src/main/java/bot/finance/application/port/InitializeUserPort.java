package bot.finance.application.port;

import bot.finance.application.dto.NewUser;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.User;

public interface InitializeUserPort {

    /**
     * @throws InvalidUserException if the command is absent
     * @throws PersistenceFailedException if storing the user fails
     */
    User initialize(NewUser newUser);

}
