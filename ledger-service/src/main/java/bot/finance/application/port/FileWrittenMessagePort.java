package bot.finance.application.port;

import bot.finance.application.dto.FileWrittenMessageCommand;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.exception.WrittenMessageInProgressException;

public interface FileWrittenMessagePort {

    /**
     * @throws EntityNotFoundException if the command's identity names no stored user
     * @throws InvalidWrittenMessageException if the text is longer than the stored width
     * @throws WrittenMessageInProgressException if the caller already has an unsettled text
     * @throws PersistenceFailedException if the write fails
     */
    WrittenMessageView file(FileWrittenMessageCommand command);
}
