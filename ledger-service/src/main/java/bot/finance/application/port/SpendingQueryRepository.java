package bot.finance.application.port;

import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.SpendingQuery;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.SpendingPeriod;
import java.util.List;

public interface SpendingQueryRepository {

    /**
     * @throws PersistenceFailedException if the write fails
     */
    SpendingQuery create(SpendingQuery query);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    List<SpendingPeriod> findPeriodsByMessageReference(long userId, MessageReference reference);

    /**
     * Removes the periods asked about under one message, once the report carrying them has reached the user.
     *
     * @return how many were removed
     * @throws PersistenceFailedException if the write fails
     */
    int discard(long userId, MessageReference reference);
}
