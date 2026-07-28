package bot.finance.adapter.aiconnector;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.domain.value.Intent;

import java.util.List;

public final class IntentProtoUtils {

    private IntentProtoUtils() {}

    public static ExtractIntentsRequest toProtoRequest(IntentExtractionRequest request) {
        // TODO: GU07 builds the generated ExtractIntentsRequest from request's text and knownCategories,
        // setting default_currency only when request.defaultCurrency() is present.
        return ExtractIntentsRequest.getDefaultInstance();
    }

    public static List<Intent> toIntents(ExtractIntentsResponse response) {
        // TODO: GU07 maps each generated entry onto a domain Intent (CategoryIntent, ExpenseIntent), in
        // order, falling back to UnknownIntent for anything it cannot map: an unrecognized or
        // unspecified operation, a missing payload, a currency ISO 4217 does not know, or a shape the
        // domain values reject.
        return List.of();
    }
}
