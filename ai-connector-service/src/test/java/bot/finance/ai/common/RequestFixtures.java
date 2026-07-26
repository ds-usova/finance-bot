package bot.finance.ai.common;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;

import java.util.List;

/**
 * Builders for a valid {@link ExtractIntentsRequest}, always carrying a non-empty {@code known_categories} —
 * an empty list is {@code INVALID_ARGUMENT}, so a request that omits it fails before reaching the behaviour
 * under test.
 */
public final class RequestFixtures {

    public static final List<String> DEFAULT_KNOWN_CATEGORIES = List.of("Food", "Travel", "Other");
    private static final String DEFAULT_TEXT = "spent 15 euros on lunch";

    private RequestFixtures() {
    }

    public static ExtractIntentsRequest request() {
        return request(DEFAULT_TEXT);
    }

    public static ExtractIntentsRequest request(String text) {
        return request(text, DEFAULT_KNOWN_CATEGORIES);
    }

    public static ExtractIntentsRequest request(String text, List<String> knownCategories) {
        return ExtractIntentsRequest.newBuilder()
                .setText(text)
                .addAllKnownCategories(knownCategories)
                .build();
    }

    public static ExtractIntentsRequest request(String text, List<String> knownCategories, String defaultCurrency) {
        return ExtractIntentsRequest.newBuilder()
                .setText(text)
                .addAllKnownCategories(knownCategories)
                .setDefaultCurrency(defaultCurrency)
                .build();
    }

}
