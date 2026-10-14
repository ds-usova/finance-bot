package bot.finance.adapter.mcp;

import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.exception.InvalidSpendingQueryException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;

public record ToolRefusal(Kind kind, String message) {

    public enum Kind {
        INVALID_REQUEST,
        UNKNOWN_USER,
        STORE_FAILED,
        UNEXPECTED
    }

    public static ToolRefusal ofExpenseProposal(RuntimeException e) {
        if (e instanceof InvalidExpenseException
                || e instanceof InvalidUserException
                || e instanceof InvalidMoneyException) {
            return invalidRequest(e);
        }
        if (e instanceof InvalidCategoryException || e instanceof InvalidGroupingException) {
            return new ToolRefusal(Kind.INVALID_REQUEST, e.getMessage());
        }
        return otherwise(e, "the proposal could not be stored", "the proposal could not be created");
    }

    public static ToolRefusal ofSpendingSummary(RuntimeException e) {
        if (e instanceof InvalidSpendingPeriodException) {
            return new ToolRefusal(Kind.INVALID_REQUEST, e.getMessage());
        }
        if (e instanceof InvalidSpendingQueryException || e instanceof InvalidUserException) {
            return invalidRequest(e);
        }
        return otherwise(e, "the summary could not be recorded", "the spending could not be summarized");
    }

    static ToolRefusal ofCategoryListing(RuntimeException e) {
        if (e instanceof InvalidCategoryException || e instanceof InvalidGroupingException) {
            return new ToolRefusal(Kind.INVALID_REQUEST, e.getMessage());
        }
        if (e instanceof InvalidUserException) {
            return invalidRequest(e);
        }
        return otherwise(e, "the categories could not be read", "the categories could not be listed");
    }

    String meterReason(RuntimeException e) {
        return kind == Kind.UNEXPECTED ? "unexpected" : e.getClass().getSimpleName();
    }

    private static ToolRefusal invalidRequest(RuntimeException e) {
        return new ToolRefusal(Kind.INVALID_REQUEST, "invalid request: " + e.getMessage());
    }

    private static ToolRefusal otherwise(RuntimeException e, String storeFailure, String unexpected) {
        if (e instanceof EntityNotFoundException) {
            return new ToolRefusal(Kind.UNKNOWN_USER, "the user is unknown");
        }
        if (e instanceof PersistenceFailedException) {
            return new ToolRefusal(Kind.STORE_FAILED, storeFailure);
        }
        return new ToolRefusal(Kind.UNEXPECTED, unexpected);
    }
}
