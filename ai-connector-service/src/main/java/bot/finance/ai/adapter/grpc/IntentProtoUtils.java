package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.domain.value.CategoryIntent;
import bot.finance.ai.domain.value.ExpenseIntent;
import bot.finance.ai.domain.value.Intent;
import bot.finance.ai.domain.value.Money;
import bot.finance.ai.domain.value.Operation;
import bot.finance.ai.domain.value.UnknownIntent;

import java.util.List;

public final class IntentProtoUtils {

    private IntentProtoUtils() { }

    public static ExtractIntentsResponse toResponse(List<Intent> intents) {
        ExtractIntentsResponse.Builder response = ExtractIntentsResponse.newBuilder();
        intents.forEach(intent -> response.addIntents(toProtoIntent(intent)));
        return response.build();
    }

    private static bot.finance.ai.adapter.grpc.v1.Intent toProtoIntent(Intent intent) {
        return switch (intent) {
            case CategoryIntent category -> bot.finance.ai.adapter.grpc.v1.Intent.newBuilder()
                    .setOperation(toProtoOperation(category.operation()))
                    .setCategory(toProtoCategory(category))
                    .build();

            case ExpenseIntent expense -> bot.finance.ai.adapter.grpc.v1.Intent.newBuilder()
                    .setOperation(toProtoOperation(expense.operation()))
                    .setExpense(toProtoExpense(expense))
                    .build();

            case UnknownIntent unknown -> bot.finance.ai.adapter.grpc.v1.Intent.newBuilder()
                    .setOperation(bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_UNKNOWN)
                    .setReason(unknown.reason())
                    .build();
        };
    }

    private static bot.finance.ai.adapter.grpc.v1.CategoryIntent toProtoCategory(CategoryIntent category) {
        bot.finance.ai.adapter.grpc.v1.CategoryIntent.Builder builder =
                bot.finance.ai.adapter.grpc.v1.CategoryIntent.newBuilder()
                        .setName(category.name());
        category.newName().ifPresent(builder::setNewName);
        return builder.build();
    }

    private static bot.finance.ai.adapter.grpc.v1.ExpenseIntent toProtoExpense(ExpenseIntent expense) {
        bot.finance.ai.adapter.grpc.v1.ExpenseIntent.Builder builder =
                bot.finance.ai.adapter.grpc.v1.ExpenseIntent.newBuilder();
        expense.categoryName().ifPresent(builder::setCategoryName);
        expense.amount().ifPresent(money -> builder.setAmount(toProtoMoney(money)));
        expense.description().ifPresent(builder::setDescription);
        return builder.build();
    }

    private static bot.finance.ai.adapter.grpc.v1.Money toProtoMoney(Money money) {
        return bot.finance.ai.adapter.grpc.v1.Money.newBuilder()
                .setMinorUnits(money.minorUnits())
                .setCurrency(money.currencyCode().code())
                .build();
    }

    private static bot.finance.ai.adapter.grpc.v1.Operation toProtoOperation(Operation operation) {
        return switch (operation) {
            case CREATE -> bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_CREATE;
            case READ -> bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_READ;
            case UPDATE -> bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_UPDATE;
            case DELETE -> bot.finance.ai.adapter.grpc.v1.Operation.OPERATION_DELETE;
        };
    }

}
