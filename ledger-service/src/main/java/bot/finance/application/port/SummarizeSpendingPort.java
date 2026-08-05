package bot.finance.application.port;

import bot.finance.application.dto.SummarizeSpendingCommand;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.exception.InvalidSpendingQueryException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.SpendingPeriod;

public interface SummarizeSpendingPort {

    /**
     * @throws InvalidSpendingQueryException if the command is absent or invalid
     * @throws InvalidSpendingPeriodException if the written dates do not make a period
     * @throws EntityNotFoundException if the token's subject names no stored user
     * @throws PersistenceFailedException if the write fails
     */
    SpendingPeriod summarize(SummarizeSpendingCommand command);
}
