package bot.finance.adapter.persistence;

import bot.finance.domain.value.ExpenseStatus;

/**
 * The facts a spending write publishes. The pending and recorded arms of a write differ only by status, so the
 * pairing is stated here rather than repeated as a ternary at each call site.
 */
public enum LedgerEventType {
    ProposalCreated,
    ProposalRefiled,
    ProposalDiscarded,
    ProposalAccepted,
    ExpenseRecorded,
    ExpenseRefiled;

    public static LedgerEventType created(ExpenseStatus status) {
        return status == ExpenseStatus.PENDING ? ProposalCreated : ExpenseRecorded;
    }

    public static LedgerEventType refiled(ExpenseStatus status) {
        return status == ExpenseStatus.PENDING ? ProposalRefiled : ExpenseRefiled;
    }
}
