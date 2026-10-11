package bot.finance.application.port;

import bot.finance.application.dto.BrowseWrittenMessagesCommand;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import java.util.List;

public interface BrowseWrittenMessagesPort {

    /**
     * Answers the caller's unsettled texts, oldest first.
     *
     * @throws EntityNotFoundException if the command's identity names no stored user
     * @throws PersistenceFailedException if the read fails
     */
    List<WrittenMessageView> browse(BrowseWrittenMessagesCommand command);
}
