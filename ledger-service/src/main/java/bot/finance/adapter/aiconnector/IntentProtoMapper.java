package bot.finance.adapter.aiconnector;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.application.dto.IntentExtractionRequest;
import java.time.format.DateTimeFormatter;

public final class IntentProtoMapper {

    /** `YYYY-MM-DD`, the form the schema agrees `current_date` crosses in. Named rather than left to a default. */
    private static final DateTimeFormatter WIRE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private IntentProtoMapper() {}

    public static ExtractIntentsRequest toProtoRequest(IntentExtractionRequest request) {
        ExtractIntentsRequest.Builder builder =
                ExtractIntentsRequest.newBuilder().setText(request.text());
        builder.addAllCategoryGroupings(request.categoryGroupings());
        builder.setCatchAllGrouping(request.catchAllGrouping());
        request.defaultCurrency().ifPresent(currency -> builder.setDefaultCurrency(currency.code()));
        builder.setCurrentDate(WIRE_DATE.format(request.currentDate()));
        return builder.build();
    }
}
