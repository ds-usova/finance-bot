package bot.finance.domain.exception;

public class ExpenseEntryNotFoundException extends EntityNotFoundException {

    public ExpenseEntryNotFoundException(String message) {
        super("entry", message);
    }
}
