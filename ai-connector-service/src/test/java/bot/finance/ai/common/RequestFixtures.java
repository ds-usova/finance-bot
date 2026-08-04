package bot.finance.ai.common;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import java.util.List;

/**
 * Builders for a valid {@link ExtractIntentsRequest}, always carrying a non-empty {@code category_groupings} — an
 * empty list is {@code INVALID_ARGUMENT}, so a request that omits it fails before reaching the behaviour under
 * test.
 */
public final class RequestFixtures {

    public static final List<String> DEFAULT_CATEGORY_GROUPINGS = List.of("Food", "Insurance", "Other");
    public static final String DEFAULT_CATCH_ALL = "Other";
    private static final String DEFAULT_TEXT = "spent 15 euros on lunch";

    private RequestFixtures() {}

    public static ExtractIntentsRequest request() {
        return request(DEFAULT_TEXT);
    }

    public static ExtractIntentsRequest request(String text) {
        return request(text, DEFAULT_CATEGORY_GROUPINGS, DEFAULT_CATCH_ALL);
    }

    public static ExtractIntentsRequest request(String text, List<String> categoryGroupings, String catchAllGrouping) {
        return ExtractIntentsRequest.newBuilder()
                .setText(text)
                .addAllCategoryGroupings(categoryGroupings)
                .setCatchAllGrouping(catchAllGrouping)
                .build();
    }

    public static ExtractIntentsRequest request(
            String text, List<String> categoryGroupings, String catchAllGrouping, String defaultCurrency) {
        return ExtractIntentsRequest.newBuilder()
                .setText(text)
                .addAllCategoryGroupings(categoryGroupings)
                .setCatchAllGrouping(catchAllGrouping)
                .setDefaultCurrency(defaultCurrency)
                .build();
    }
}
