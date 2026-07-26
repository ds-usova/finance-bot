package bot.finance.ai.common;

import bot.finance.ai.application.dto.RawIntent;
import bot.finance.ai.domain.value.CategoryIntent;
import bot.finance.ai.domain.value.ExpenseIntent;
import bot.finance.ai.domain.value.Money;
import bot.finance.ai.domain.value.Operation;
import bot.finance.ai.domain.value.UnknownIntent;

import java.util.Optional;

/**
 * Domain {@code Intent} and {@code Money} builders shared across unit and integration tests.
 */
public final class IntentFixtures {

    private IntentFixtures() {
    }

    public static Money money() {
        return money("15.00", "EUR");
    }

    public static Money money(String amount, String currency) {
        return Money.of(amount, currency);
    }

    public static CategoryIntent categoryIntent(Operation operation, String name, Optional<String> newName) {
        return new CategoryIntent(operation, name, newName);
    }

    /**
     * A {@link CategoryIntent} valid for {@code operation}: carries a new name when the operation is
     * {@code UPDATE}, since the compact constructor rejects an UPDATE with none.
     */
    public static CategoryIntent categoryIntent(Operation operation) {
        Optional<String> newName = operation == Operation.UPDATE ? Optional.of("Groceries") : Optional.empty();
        return categoryIntent(operation, "Food", newName);
    }

    public static ExpenseIntent expenseIntent(
            Operation operation, Optional<String> categoryName, Optional<Money> amount, Optional<String> description) {
        return new ExpenseIntent(operation, categoryName, amount, description);
    }

    /**
     * An {@link ExpenseIntent} valid for {@code operation}: carries both a category and an amount when the
     * operation is {@code CREATE}, since the compact constructor rejects a CREATE missing either.
     */
    public static ExpenseIntent expenseIntent(Operation operation) {
        if (operation == Operation.CREATE) {
            return expenseIntent(operation, Optional.of("Food"), Optional.of(money()), Optional.of("lunch"));
        }
        return expenseIntent(operation, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static UnknownIntent unknownIntent(String reason) {
        return new UnknownIntent(reason);
    }

    public static UnknownIntent unknownIntent() {
        return unknownIntent("could not classify the message");
    }

    public static RawIntent rawIntent(
            String target, String operation, String categoryName, String newCategoryName,
            String amount, String currency, String description) {
        return new RawIntent(target, operation, categoryName, newCategoryName, amount, currency, description);
    }

}
