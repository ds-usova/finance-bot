package bot.finance.application.port;

import bot.finance.application.dto.BrowseCategoriesCommand;
import bot.finance.application.dto.CategoryEntry;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.List;

public interface BrowseCategoriesPort {

    /**
     * @throws EntityNotFoundException if the command's identity names no stored user
     * @throws PersistenceFailedException if reading the categories fails
     */
    List<CategoryEntry> browse(BrowseCategoriesCommand command);
}
