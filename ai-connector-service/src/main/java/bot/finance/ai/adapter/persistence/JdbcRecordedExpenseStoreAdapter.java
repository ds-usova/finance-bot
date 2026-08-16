package bot.finance.ai.adapter.persistence;

import bot.finance.ai.application.port.RecordedExpenseStorePort;
import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.SpendingRow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
public class JdbcRecordedExpenseStoreAdapter implements RecordedExpenseStorePort {

    private final RecordedExpenseEntityRepository repository;
    private final IncomingMessageEntityRepository messageRepository;

    public JdbcRecordedExpenseStoreAdapter(
            RecordedExpenseEntityRepository repository, IncomingMessageEntityRepository messageRepository) {
        this.repository = repository;
        this.messageRepository = messageRepository;
    }

    @Override
    public void recordProposed(SpendingRow proposal) {
        // upserts a PROPOSED row keyed by proposal_id, category id and names taking the later copy's, status
        // untouched on conflict; a message the store lacks is a no-op
    }

    @Override
    @Transactional
    public void settleProposalDeleted(SpendingRow proposal, String transactionId) {
        // under the message's row lock: an unpaired lone ACCEPTED row of this transaction, same content, takes
        // the proposal id, and the PROPOSED row's own copy is dropped; else the PROPOSED row becomes DISCARDED,
        // remembering the transaction; a message the store lacks is a no-op
    }

    @Override
    @Transactional
    public void settleExpenseInserted(SpendingRow expense, String transactionId) {
        // under the message's row lock: a row already keyed by this expense id takes the entry's names; else the
        // lowest content-equal DISCARDED row of this transaction becomes ACCEPTED and takes the expense id; else
        // a lone ACCEPTED row is inserted remembering the transaction; a message the store lacks is a no-op
    }

    @Override
    public void refileExpense(SpendingRow expense) {
        // the row keyed by expense_id takes the entry's new category id and names; a no-op if none matches
    }

    @Override
    public void removeExpense(long expenseId) {
        // deletes the row keyed by expense_id; the message row stays; a no-op if none matches
    }

    @Override
    public void renameCategory(long categoryId, String name) {
        // every row under categoryId takes the new name; a no-op if none matches
    }

    @Override
    public void renameGrouping(long userId, String from, String to) {
        // every row of userId under grouping "from" takes "to"; a no-op if none matches
    }

    @Override
    public void abandonAcceptance(MessageIdentity message, String transactionId) {
        // every DISCARDED row of that message with that transaction becomes UNKNOWN; a no-op if none matches
    }
}
