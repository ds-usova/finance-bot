package bot.finance.ai.application.port;

import bot.finance.ai.domain.value.MessageIdentity;
import bot.finance.ai.domain.value.SpendingRow;

public interface RecordedExpenseStorePort {

    void recordProposed(SpendingRow proposal);

    void settleProposalDeleted(SpendingRow proposal, String transactionId);

    void settleExpenseInserted(SpendingRow expense, String transactionId);

    void refileExpense(SpendingRow expense);

    void removeExpense(long expenseId);

    void renameCategory(long categoryId, String name);

    void renameGrouping(long userId, String from, String to);

    void abandonAcceptance(MessageIdentity message, String transactionId);
}
