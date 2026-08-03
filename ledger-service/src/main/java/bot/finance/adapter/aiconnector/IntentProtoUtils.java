package bot.finance.adapter.aiconnector;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.dto.KnownCategory;

public final class IntentProtoUtils {

    private IntentProtoUtils() {}

    public static ExtractIntentsRequest toProtoRequest(IntentExtractionRequest request) {
        ExtractIntentsRequest.Builder builder =
                ExtractIntentsRequest.newBuilder().setText(request.text());
        request.knownCategories().stream()
                .map(IntentProtoUtils::toProtoKnownCategory)
                .forEach(builder::addKnownCategories);
        request.defaultCurrency().ifPresent(currency -> builder.setDefaultCurrency(currency.code()));
        return builder.build();
    }

    private static bot.finance.ai.adapter.grpc.v1.KnownCategory toProtoKnownCategory(KnownCategory category) {
        return bot.finance.ai.adapter.grpc.v1.KnownCategory.newBuilder()
                .setName(category.name())
                .setParentName(category.parentName())
                .build();
    }
}
