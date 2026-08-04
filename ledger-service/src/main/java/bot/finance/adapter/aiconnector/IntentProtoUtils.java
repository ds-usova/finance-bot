package bot.finance.adapter.aiconnector;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.application.dto.IntentExtractionRequest;

public final class IntentProtoUtils {

    private IntentProtoUtils() {}

    public static ExtractIntentsRequest toProtoRequest(IntentExtractionRequest request) {
        ExtractIntentsRequest.Builder builder =
                ExtractIntentsRequest.newBuilder().setText(request.text());
        builder.addAllCategoryGroupings(request.categoryGroupings());
        builder.setCatchAllGrouping(request.catchAllGrouping());
        request.defaultCurrency().ifPresent(currency -> builder.setDefaultCurrency(currency.code()));
        return builder.build();
    }
}
