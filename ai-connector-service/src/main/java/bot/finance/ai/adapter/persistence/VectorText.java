package bot.finance.ai.adapter.persistence;

import bot.finance.ai.domain.value.Embedding;

final class VectorText {

    private VectorText() {}

    static String toLiteral(Embedding embedding) {
        // pgvector's "[a,b,c]" text form, with no spaces
        return null;
    }

    static Embedding fromLiteral(String literal) {
        // parses pgvector's "[a,b,c]" text form back into an Embedding, in order
        return null;
    }
}
