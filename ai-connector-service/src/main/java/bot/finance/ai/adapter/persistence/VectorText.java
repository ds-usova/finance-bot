package bot.finance.ai.adapter.persistence;

import bot.finance.ai.domain.value.Embedding;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

final class VectorText {

    private VectorText() {}

    static String toLiteral(Embedding embedding) {
        // pgvector's "[a,b,c]" text form, with no spaces
        return embedding.values().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    static Embedding fromLiteral(String literal) {
        // parses pgvector's "[a,b,c]" text form back into an Embedding, in order
        String inner = literal.substring(1, literal.length() - 1);
        List<Float> values = Arrays.stream(inner.split(",")).map(Float::parseFloat).toList();
        return new Embedding(values);
    }
}
