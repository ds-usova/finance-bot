package bot.finance.application.port;

import bot.finance.application.dto.CurrencyTotal;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.ExpenseFilter;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository {

    /**
     * @throws InvalidExpenseException if a value violates a column constraint
     * @throws EntityNotFoundException if the category id names no stored category, or the user id names no
     *     stored user
     * @throws PersistenceFailedException if the write fails
     */
    Expense create(Expense expense);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    int countByMessageReference(long userId, IncomingMessageId reference);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    List<CurrencyTotal> totalsByCurrency(long userId, SpendingPeriod period);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    List<ExpenseEntry> findPage(long userId, ExpenseFilter filter);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    long countMatching(long userId, ExpenseFilter filter);

    /**
     * Refiles the caller's recorded expense under a new category, answering the row as it now stands. An empty
     * result means no row of the caller's carried that id.
     *
     * @throws PersistenceFailedException if the write fails
     */
    Optional<ExpenseEntry> refile(long userId, long entryId, long categoryId, Instant now);
}
