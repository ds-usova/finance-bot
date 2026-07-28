package bot.finance.adapter.aiconnector;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.domain.exception.InvalidIntentException;
import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.value.CategoryIntent;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseIntent;
import bot.finance.domain.value.Intent;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.Operation;
import bot.finance.domain.value.UnknownIntent;
import java.util.List;
import java.util.Optional;

public final class IntentProtoUtils {

    private IntentProtoUtils() {}

    public static ExtractIntentsRequest toProtoRequest(IntentExtractionRequest request) {
        ExtractIntentsRequest.Builder builder = ExtractIntentsRequest.newBuilder()
                .setText(request.text())
                .addAllKnownCategories(request.knownCategories());
        request.defaultCurrency().ifPresent(currency -> builder.setDefaultCurrency(currency.code()));
        return builder.build();
    }

    public static List<Intent> toIntents(ExtractIntentsResponse response) {
        return response.getIntentsList().stream()
                .map(IntentProtoUtils::toIntent)
                .toList();
    }

    private static Intent toIntent(bot.finance.ai.adapter.grpc.v1.Intent entry) {
        try {
            return switch (entry.getOperation()) {
                case OPERATION_UNKNOWN -> unknownIntent(entry.getReason());
                case OPERATION_CREATE -> toIntentForPayload(Operation.CREATE, entry);
                case OPERATION_READ -> toIntentForPayload(Operation.READ, entry);
                case OPERATION_UPDATE -> toIntentForPayload(Operation.UPDATE, entry);
                case OPERATION_DELETE -> toIntentForPayload(Operation.DELETE, entry);
                case OPERATION_UNSPECIFIED, UNRECOGNIZED -> unrecognizedOperationIntent(entry);
            };
        } catch (InvalidIntentException | InvalidMoneyException e) {
            // A shape the domain rejects becomes that one entry's reason; the rest of the response still maps.
            return new UnknownIntent(e.getMessage());
        }
    }

    private static Intent unknownIntent(String reason) {
        String message = (reason == null || reason.isBlank()) ? "Unknown reason" : reason;
        return new UnknownIntent(message);
    }

    private static Intent unrecognizedOperationIntent(bot.finance.ai.adapter.grpc.v1.Intent entry) {
        return new UnknownIntent(
                "Unrecognized operation value " + entry.getOperationValue() + " (" + entry.getOperation() + ")");
    }

    private static Intent toIntentForPayload(Operation operation, bot.finance.ai.adapter.grpc.v1.Intent entry) {
        return switch (entry.getPayloadCase()) {
            case CATEGORY -> toCategoryIntent(operation, entry.getCategory());
            case EXPENSE -> toExpenseIntent(operation, entry.getExpense());
            case PAYLOAD_NOT_SET ->
                new UnknownIntent("Entry with operation " + operation + " carried no category or expense payload");
        };
    }

    private static CategoryIntent toCategoryIntent(
            Operation operation, bot.finance.ai.adapter.grpc.v1.CategoryIntent category) {
        Optional<String> newName = category.hasNewName() ? Optional.of(category.getNewName()) : Optional.empty();
        return new CategoryIntent(operation, category.getName(), newName);
    }

    private static ExpenseIntent toExpenseIntent(
            Operation operation, bot.finance.ai.adapter.grpc.v1.ExpenseIntent expense) {
        Optional<String> categoryName =
                expense.hasCategoryName() ? Optional.of(expense.getCategoryName()) : Optional.empty();
        Optional<Money> amount = expense.hasAmount() ? Optional.of(toMoney(expense.getAmount())) : Optional.empty();
        Optional<String> description =
                expense.hasDescription() ? Optional.of(expense.getDescription()) : Optional.empty();
        return new ExpenseIntent(operation, categoryName, amount, description);
    }

    private static Money toMoney(bot.finance.ai.adapter.grpc.v1.Money money) {
        return new Money(money.getMinorUnits(), new CurrencyCode(money.getCurrency()));
    }
}
