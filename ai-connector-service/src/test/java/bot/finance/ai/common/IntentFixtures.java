package bot.finance.ai.common;

import bot.finance.ai.application.dto.RawIntent;
import bot.finance.ai.domain.value.ExpenseIntent;
import bot.finance.ai.domain.value.Money;
import bot.finance.ai.domain.value.Operation;

import java.util.Optional;

/**
 * {@code ExpenseIntent}, {@code Money} and {@code RawIntent} builders shared across unit and integration tests.
 */
public final class IntentFixtures {

    private IntentFixtures() {
    }

    public static Money money() {
        return Money.of("15.00", "EUR");
    }

    /**
     * An {@link ExpenseIntent} valid for {@code operation}: carries a category, an amount and a description
     * when the operation is {@code CREATE}, since the compact constructor rejects a CREATE missing any of them.
     * The parent category name is empty.
     */
    public static ExpenseIntent expenseIntent(Operation operation) {
        if (operation == Operation.CREATE) {
            return new ExpenseIntent(
                    operation, Optional.of("Food"), Optional.of(money()), Optional.of("lunch"), Optional.empty());
        }
        return new ExpenseIntent(
                operation, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * A {@code CREATE} {@link ExpenseIntent} carrying the given parent category name and description, for
     * scenarios that need the grouping ExtractIntentsUseCase matched or the description a proposal carries.
     */
    public static ExpenseIntent expenseIntentWithParentAndDescription(
            String categoryName, String parentCategoryName, String description) {
        return new ExpenseIntent(
                Operation.CREATE,
                Optional.of(categoryName),
                Optional.of(money()),
                Optional.of(description),
                Optional.of(parentCategoryName));
    }

    public static RawIntent rawIntent(
            String target, String operation, String categoryName, String newCategoryName,
            String amount, String currency, String description) {
        return new RawIntent(target, operation, categoryName, newCategoryName, amount, currency, description);
    }

}
