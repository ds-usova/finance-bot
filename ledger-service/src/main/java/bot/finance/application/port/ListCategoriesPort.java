package bot.finance.application.port;

import bot.finance.application.dto.ListCategoriesCommand;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.List;

public interface ListCategoriesPort {

    /**
     * @throws InvalidCategoryException if the command is invalid, or names no stored grouping
     * @throws EntityNotFoundException if the command's identity names no stored user
     * @throws PersistenceFailedException if reading the categories fails
     */
    List<String> list(ListCategoriesCommand command);
}
