package bot.finance.ai.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.domain.value.Embedding;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VectorTextTest {

    @Nested
    @DisplayName("toLiteral()")
    class ToLiteral {

        @Test
        @DisplayName("when an embedding of three components is given - then answers the bracketed, "
                + "comma-separated form with no spaces")
        void whenEmbeddingOfThreeComponentsGiven_thenAnswersBracketedCommaSeparatedFormWithNoSpaces() {
            Embedding embedding = new Embedding(List.of(0.1f, 0.2f, 0.3f));

            String literal = VectorText.toLiteral(embedding);

            assertThat(literal).isEqualTo("[0.1,0.2,0.3]");
        }
    }

    @Nested
    @DisplayName("fromLiteral()")
    class FromLiteral {

        @Test
        @DisplayName("when that literal is parsed - then answers an embedding with the same components, in order")
        void whenThatLiteralIsParsed_thenAnswersEmbeddingWithSameComponentsInOrder() {
            Embedding embedding = VectorText.fromLiteral("[0.1,0.2,0.3]");

            assertThat(embedding.values()).containsExactly(0.1f, 0.2f, 0.3f);
        }

        @Test
        @DisplayName("when the literal holds a negative and an exponent-formatted component - "
                + "then both read back unchanged")
        void whenLiteralHoldsNegativeAndExponentFormattedComponent_thenBothReadBackUnchanged() {
            Embedding embedding = VectorText.fromLiteral("[-1.5,2.5E-3]");

            assertThat(embedding.values()).containsExactly(-1.5f, 2.5E-3f);
        }
    }
}
