package bot.finance.application.port;

import bot.finance.application.dto.CurrencyTotal;
import bot.finance.application.dto.ExpenseEntry;
import bot.finance.application.dto.ProposalSummary;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.ExpenseFilter;
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
     * Accepts the caller's PENDING entries under that message, answering how many were accepted.
     *
     * @throws PersistenceFailedException if the write fails
     */
    int accept(long userId, IncomingMessageId reference, Instant now);

    /**
     * Discards the caller's PENDING entries under that message, answering how many were discarded.
     *
     * @throws PersistenceFailedException if the write fails
     */
    int discard(long userId, IncomingMessageId reference, Instant now);

    /**
     * Accepts the caller's PENDING entries named by id, answering the message each was reported on.
     *
     * @throws PersistenceFailedException if the write fails
     */
    List<IncomingMessageId> acceptByIds(long userId, ProposalIds ids, Instant now);

    /**
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
     * Files the caller's entry under a new category, answering it as it now stands. An empty result means no
     * entry of theirs carries that id.
     *
     * @throws PersistenceFailedException if the write fails
     */
    Optional<ExpenseEntry> refile(long userId, long entryId, long categoryId, Instant now);
}
