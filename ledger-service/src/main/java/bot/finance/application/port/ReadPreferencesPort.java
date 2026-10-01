package bot.finance.application.port;

import bot.finance.application.dto.Preferences;
import bot.finance.application.dto.ReadPreferencesCommand;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;

public interface ReadPreferencesPort {

    /**
     * @throws EntityNotFoundException if the command's identity names no stored user
     * @throws PersistenceFailedException if reading the preferences fails
     */
    Preferences read(ReadPreferencesCommand command);
}
