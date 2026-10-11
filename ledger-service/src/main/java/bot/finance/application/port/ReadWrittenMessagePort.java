package bot.finance.application.port;

import bot.finance.application.dto.ReadWrittenMessageCommand;
import bot.finance.application.dto.WrittenMessageAnswer;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.exception.WrittenMessageNotFoundException;

public interface ReadWrittenMessagePort {

    /**
     * @throws EntityNotFoundException if the command's identity names no stored user
     * @throws WrittenMessageNotFoundException if the caller has no message under that id
     * @throws PersistenceFailedException if the read fails
     */
    WrittenMessageAnswer read(ReadWrittenMessageCommand command);
}
