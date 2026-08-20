package bot.finance.ai.common.fixtures;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 1536-component vectors — the default embedding model's width — and the provider's embeddings response body
 * serving them, for a test that reasons about cosine similarity rather than about a vector's literal values.
 */
public final class EmbeddingFixtures {

    public static final int DIMENSIONS = 1536;

    private EmbeddingFixtures() {}

    /** A unit vector with {@code 1} at {@code axis} and {@code 0} everywhere else. */
    public static List<Float> unitVector(int axis) {
        return IntStream.range(0, DIMENSIONS).mapToObj(i -> i == axis ? 1f : 0f).toList();
    }

    /**
     * A unit vector at cosine similarity {@code similarity} to {@link #unitVector(int) unitVector(axis)}, spread
     * over {@code axis} and the next component.
     */
    public static List<Float> unitVectorAt(int axis, double similarity) {
        double other = Math.sqrt(1 - similarity * similarity);
        return IntStream.range(0, DIMENSIONS)
                .mapToObj(i -> {
                    if (i == axis) {
                        return (float) similarity;
                    }
                    if (i == axis + 1) {
                        return (float) other;
                    }
                    return 0f;
                })
                .toList();
    }

    /** The provider's embeddings response body carrying {@code vector} alone. */
    public static String embeddingsResponse(List<Float> vector) {
        return embeddingsResponseForAll(List.of(vector));
    }

    /** The provider's embeddings response body carrying one entry per element of {@code vectors}, in order. */
    public static String embeddingsResponseForAll(List<List<Float>> vectors) {
        String data = IntStream.range(0, vectors.size())
                .mapToObj(index -> """
                        {"object":"embedding","index":%d,"embedding":%s}"""
                        .formatted(index, jsonArray(vectors.get(index))))
                .collect(Collectors.joining(","));
        return """
                {"object":"list","data":[%s],"model":"text-embedding-3-small",\
                "usage":{"prompt_tokens":1,"total_tokens":1}}"""
                .formatted(data);
    }

    private static String jsonArray(List<Float> vector) {
        return vector.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }
}
