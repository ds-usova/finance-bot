package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EmbeddingTest {

    @Nested
    @DisplayName("constructing an Embedding")
    class CompactConstructor {

        @Test
        @DisplayName("when a list of components is given - "
                + "then they read back unchanged, and mutating the list afterwards leaves them alone")
        void whenListOfComponentsGiven_thenTheyReadBackUnchangedAndMutatingListAfterwardsLeavesThemAlone() {
            List<Float> components = new ArrayList<>(Arrays.asList(0.1f, 0.2f, 0.3f));

            Embedding embedding = new Embedding(components);
            components.add(0.4f);

            assertThat(embedding.values()).containsExactly(0.1f, 0.2f, 0.3f);
        }

        static Stream<Arguments> invalidListScenarios() {
            return Stream.of(
                    Arguments.of(Named.of("null list", (List<Float>) null)),
                    Arguments.of(Named.of("empty list", Collections.<Float>emptyList())),
                    Arguments.of(Named.of("list holding a null", Arrays.asList(0.1f, null))));
        }

        @ParameterizedTest
        @MethodSource("invalidListScenarios")
        @DisplayName("when the list is null, empty, or holds a null - then throws InvalidValueException")
        void whenListIsNullEmptyOrHoldsANull_thenThrowsInvalidValueException(List<Float> values) {
            assertThatThrownBy(() -> new Embedding(values)).isInstanceOf(InvalidValueException.class);
        }
    }
}
