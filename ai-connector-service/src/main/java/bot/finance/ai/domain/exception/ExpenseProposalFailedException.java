package bot.finance.ai.domain.exception;

public class ExpenseProposalFailedException extends RuntimeException {

    public enum Reason {
        /** The ledger refused the proposal outright; retrying the same call would not help. */
        REFUSED,
        /** The ledger could not be reached at all — a transport failure, a timeout, a missing token. */
        UNREACHABLE
    }

    private final Reason reason;

    public ExpenseProposalFailedException(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    public ExpenseProposalFailedException(String message, Reason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
