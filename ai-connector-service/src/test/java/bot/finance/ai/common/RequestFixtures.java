package bot.finance.ai.common;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.KnownCategory;
import java.util.List;

/**
 * Builders for a valid {@link ExtractIntentsRequest}, always carrying a non-empty {@code known_categories} —
 * an empty list is {@code INVALID_ARGUMENT}, so a request that omits it fails before reaching the behaviour
 * under test.
 */
public final class RequestFixtures {

    public static final List<KnownCategory> DEFAULT_KNOWN_CATEGORIES = List.of(
            knownCategory("Lunch", "Food"), knownCategory("Travel", "Insurance"), knownCategory("Other", "Other"));
    private static final String DEFAULT_TEXT = "spent 15 euros on lunch";

    private RequestFixtures() {}

    public static KnownCategory knownCategory(String name, String parentName) {
        return KnownCategory.newBuilder()
                .setName(name)
                .setParentName(parentName)
                .build();
    }

    public static ExtractIntentsRequest request() {
        return request(DEFAULT_TEXT);
    }

    public static ExtractIntentsRequest request(String text) {
        return request(text, DEFAULT_KNOWN_CATEGORIES);
    }

    public static ExtractIntentsRequest request(String text, List<KnownCategory> knownCategories) {
        return ExtractIntentsRequest.newBuilder()
                .setText(text)
                .addAllKnownCategories(knownCategories)
                .build();
    }

    public static ExtractIntentsRequest request(
            String text, List<KnownCategory> knownCategories, String defaultCurrency) {
        return ExtractIntentsRequest.newBuilder()
                .setText(text)
                .addAllKnownCategories(knownCategories)
                .setDefaultCurrency(defaultCurrency)
                .build();
    }
}
