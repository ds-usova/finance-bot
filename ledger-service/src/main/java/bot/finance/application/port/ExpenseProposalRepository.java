package bot.finance.application.port;

import bot.finance.application.dto.ProposalSummary;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.ProposalIds;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ExpenseProposalRepository {

    /**
     * @throws InvalidExpenseProposalException if a value violates a column constraint
     * @throws EntityNotFoundException if the user id or category id names no stored row
     * @throws PersistenceFailedException if the write fails
     */
    ExpenseProposal create(ExpenseProposal proposal);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    List<ProposalSummary> findSummariesByMessageReference(long userId, IncomingMessageId reference);

    /**
     * @throws PersistenceFailedException if the write fails
     */
    int accept(long userId, IncomingMessageId reference, Instant now);

    /**
     * @throws PersistenceFailedException if the write fails
     */
    int discard(long userId, IncomingMessageId reference);

    /**
     * @throws PersistenceFailedException if the write fails
     */
    List<IncomingMessageId> acceptByIds(long userId, ProposalIds ids, Instant now);

    /**
     * @throws PersistenceFailedException if the read fails
     */
    Set<IncomingMessageId> findWithPendingProposals(long userId, Collection<IncomingMessageId> ids);
}
