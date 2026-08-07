package bot.finance.application.port;

import bot.finance.application.dto.BrowseGroupingsCommand;
import bot.finance.application.dto.GroupingEntry;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.List;

public interface BrowseGroupingsPort {

    /**
     * @throws EntityNotFoundException if the command's identity names no stored user
     * @throws PersistenceFailedException if reading the groupings fails
     */
    List<GroupingEntry> browse(BrowseGroupingsCommand command);
}
