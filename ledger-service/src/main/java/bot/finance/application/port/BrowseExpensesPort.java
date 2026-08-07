package bot.finance.application.port;

import bot.finance.application.dto.BrowseExpensesCommand;
import bot.finance.application.dto.ExpensePage;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;

public interface BrowseExpensesPort {

    /**
     * @throws EntityNotFoundException if the command's identity names no stored user
     * @throws PersistenceFailedException if reading the page or the total fails
     */
    ExpensePage browse(BrowseExpensesCommand command);
}
