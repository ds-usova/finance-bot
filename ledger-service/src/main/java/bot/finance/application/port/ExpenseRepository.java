package bot.finance.application.port;

import bot.finance.application.dto.CurrencyTotal;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.ExpenseFilter;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.ProposalIds;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExpenseRepository {

    /**
     * @throws InvalidExpenseException if a value violates a column constraint
     * @throws EntityNotFoundException if the category id names no stored category, or the user id names no
     *     stored user
     * @throws PersistenceFailedException if the write fails
     */
    Expense create(Expense expense);

    /**
     * Summarizes the caller's PENDING entries under that message, oldest first, category and grouping included.
     *
     * @throws PersistenceFailedException if the read fails
     */
    List<ProposalSummary> findSummariesByMessageReference(long userId, IncomingMessageId reference);

    /**
     * Moves the caller's PENDING entries under that message to RECORDED, answering how many rows matched.
     *
     * @throws PersistenceFailedException if the write fails
     */
    int accept(long userId, IncomingMessageId reference, Instant now);

    /**
     * Removes the caller's PENDING entries under that message, answering how many rows matched.
     *
     * @throws PersistenceFailedException if the write fails
     */
    int discard(long userId, IncomingMessageId reference);

    /**
     * Moves the caller's PENDING entries named by id to RECORDED, answering each moved row's message id.
     *
     * @throws PersistenceFailedException if the write fails
     */
    List<IncomingMessageId> acceptByIds(long userId, ProposalIds ids, Instant now);

    /**
     * Answers which of the given messages still hold a PENDING entry of the caller's.
     *
     * @throws PersistenceFailedException if the read fails
     */
    Set<IncomingMessageId> findWithPendingProposals(long userId, Collection<IncomingMessageId> ids);

    /**
     * Counts the caller's RECORDED entries under that message.
     *
     * @throws PersistenceFailedException if the read fails
     */
    int countByMessageReference(long userId, IncomingMessageId reference);

    /**
     * Totals the caller's RECORDED entries within the period.
     *
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
     * Refiles the caller's entry under that status under a new category, answering the row as it now stands. An
     * empty result means no row of the caller's carried that id under that status.
     *
     * @throws PersistenceFailedException if the write fails
     */
    Optional<ExpenseEntry> refile(long userId, long entryId, long categoryId, ExpenseStatus status, Instant now);
}
