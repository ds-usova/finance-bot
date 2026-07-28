package bot.finance.common;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.domain.value.CategoryIntent;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseIntent;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.Operation;
import java.util.List;
import java.util.Optional;

/**
 * Builders for the generated {@code ExtractIntentsResponse} shapes the tests stub {@link
 * bot.finance.common.containers.GrpcStubServer} with, and for the domain {@code Intent}/{@code Money}
 * values those responses are expected to map onto. The generated schema and the domain share five
 * simple names (Intent, Money, CategoryIntent, ExpenseIntent, Operation); this class imports the domain
 * ones and fully qualifies the generated ones, as the mapper it fixtures for does.
 */
public final class IntentFixtures {

    private IntentFixtures() {}

    public static bot.finance.ai.adapter.grpc.v1.Intent categoryEntry(
            bot.finance.ai.adapter.grpc.v1.Operation operation, String name, String newName) {
        bot.finance.ai.adapter.grpc.v1.CategoryIntent.Builder category =
                bot.finance.ai.adapter.grpc.v1.CategoryIntent.newBuilder().setName(name);
        if (newName != null) {
            category.setNewName(newName);
        }
        return bot.finance.ai.adapter.grpc.v1.Intent.newBuilder()
                .setOperation(operation)
                .setCategory(category)
                .build();
    }

    public static bot.finance.ai.adapter.grpc.v1.Intent expenseEntry(
            bot.finance.ai.adapter.grpc.v1.Operation operation,
            String categoryName,
            Long minorUnits,
            String currency,
            String description) {
        bot.finance.ai.adapter.grpc.v1.ExpenseIntent.Builder expense =
                bot.finance.ai.adapter.grpc.v1.ExpenseIntent.newBuilder();
        if (categoryName != null) {
            expense.setCategoryName(categoryName);
        }
        if (minorUnits != null && currency != null) {
            expense.setAmount(bot.finance.ai.adapter.grpc.v1.Money.newBuilder()
                    .setMinorUnits(minorUnits)
                    .setCurrency(currency));
        }
        if (description != null) {
            expense.setDescription(description);
        }
        return bot.finance.ai.adapter.grpc.v1.Intent.newBuilder()
                .setOperation(operation)
                .setExpense(expense)
                .build();
    }

    public static bot.finance.ai.adapter.grpc.v1.Intent unknownEntry(String reason) {
        bot.finance.ai.adapter.grpc.v1.Intent.Builder builder = bot.finance.ai.adapter.grpc.v1.Intent.newBuilder()
                .setOperation(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_UNKNOWN);
        if (reason != null) {
            builder.setReason(reason);
        }
        return builder.build();
    }

    /** An entry carrying an operation but neither a category nor an expense payload. */
    public static bot.finance.ai.adapter.grpc.v1.Intent payloadlessEntry(
            bot.finance.ai.adapter.grpc.v1.Operation operation) {
        return bot.finance.ai.adapter.grpc.v1.Intent.newBuilder()
                .setOperation(operation)
                .build();
    }

    /** An entry whose operation is a raw number, so the generated enum reads back as UNRECOGNIZED. */
    public static bot.finance.ai.adapter.grpc.v1.Intent rawOperationValueEntry(int operationValue) {
        return bot.finance.ai.adapter.grpc.v1.Intent.newBuilder()
                .setOperationValue(operationValue)
                .build();
    }

    public static ExtractIntentsResponse response(bot.finance.ai.adapter.grpc.v1.Intent... entries) {
        return ExtractIntentsResponse.newBuilder()
                .addAllIntents(List.of(entries))
                .build();
    }

    public static CategoryIntent categoryIntent(Operation operation, String name, Optional<String> newName) {
        return new CategoryIntent(operation, name, newName);
    }

    public static ExpenseIntent expenseIntent(
            Operation operation, Optional<String> categoryName, Optional<Money> amount, Optional<String> description) {
        return new ExpenseIntent(operation, categoryName, amount, description);
    }

    public static Money money(long minorUnits, String currencyCode) {
        return new Money(minorUnits, new CurrencyCode(currencyCode));
    }
}
