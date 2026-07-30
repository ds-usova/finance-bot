package bot.finance.domain.exception;

public class InvalidExpenseProposalException extends RuntimeException {

    public InvalidExpenseProposalException(String message) {
        super(message);
    }
}
