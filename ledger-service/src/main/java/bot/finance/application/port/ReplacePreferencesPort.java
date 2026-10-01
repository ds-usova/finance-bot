package bot.finance.application.port;

import bot.finance.application.dto.Preferences;
import bot.finance.application.dto.ReplacePreferencesCommand;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;

public interface ReplacePreferencesPort {

    /**
     * @throws EntityNotFoundException if the command's identity names no stored user
     * @throws PersistenceFailedException if writing the preferences fails
     */
    Preferences replace(ReplacePreferencesCommand command);
}
