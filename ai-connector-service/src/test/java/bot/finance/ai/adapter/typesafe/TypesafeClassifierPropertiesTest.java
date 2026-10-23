package bot.finance.ai.adapter.typesafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TypesafeClassifierPropertiesTest {

    private static final URI BASE_URL = URI.create("https://api.typesafe.ai");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Nested
    @DisplayName("TypesafeClassifierProperties()")
    class Binding {

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the key is null or blank - then it throws and names TYPESAFE_API_KEY")
        void whenKeyIsNullOrBlank_thenThrowsNamingTypesafeApiKey(String key) {
            assertThatThrownBy(() -> new TypesafeClassifierProperties(key, BASE_URL, "jev-1", TIMEOUT, 0.5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TYPESAFE_API_KEY");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the model is null or blank - then it throws and names CLASSIFIER_MODEL")
        void whenModelIsNullOrBlank_thenThrowsNamingClassifierModel(String model) {
            assertThatThrownBy(() -> new TypesafeClassifierProperties("key", BASE_URL, model, TIMEOUT, 0.5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CLASSIFIER_MODEL");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(doubles = {-0.01, 1.01, -5, 2})
        @DisplayName(
                "when the minimum confidence is outside 0 to 1 - then it throws and names CLASSIFIER_MIN_CONFIDENCE")
        void whenMinConfidenceIsOutsideZeroToOne_thenThrowsNamingClassifierMinConfidence(double minConfidence) {
            assertThatThrownBy(() -> new TypesafeClassifierProperties("key", BASE_URL, "jev-1", TIMEOUT, minConfidence))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CLASSIFIER_MIN_CONFIDENCE");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(doubles = {0, 1})
        @DisplayName("when the minimum confidence is exactly 0 or exactly 1 - then the properties bind")
        void whenMinConfidenceIsExactlyZeroOrOne_thenPropertiesBind(double minConfidence) {
            TypesafeClassifierProperties properties =
                    new TypesafeClassifierProperties("key", BASE_URL, "jev-1", TIMEOUT, minConfidence);

            assertThat(properties.minConfidence()).isEqualTo(minConfidence);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"PT0S", "PT-1S"})
        @DisplayName("when the timeout is zero or less - then it throws and names CLASSIFIER_TIMEOUT")
        void whenTimeoutIsZeroOrLess_thenThrowsNamingClassifierTimeout(String timeout) {
            assertThatThrownBy(() ->
                            new TypesafeClassifierProperties("key", BASE_URL, "jev-1", Duration.parse(timeout), 0.5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CLASSIFIER_TIMEOUT");
        }
    }
}
