package bot.finance.ai.adapter.persistence;

import bot.finance.ai.domain.exception.MessageStoreFailedException;
import bot.finance.ai.domain.exception.MessageStoreUnavailableException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;

final class MessageStoreExceptionMapper {

    private MessageStoreExceptionMapper() {}

    static MessageStoreFailedException toDomain(DataAccessException e, String message) {
        if (e instanceof DataAccessResourceFailureException || e instanceof TransientDataAccessException) {
            return new MessageStoreUnavailableException(message, e);
        }
        return new MessageStoreFailedException(message, e);
    }
}
