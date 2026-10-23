package bot.finance.ai.adapter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExtractionPropertiesTest {

    @Nested
    @DisplayName("ExtractionProperties()")
    class Binding {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"rules", "", "CLASSIFIER"})
        @DisplayName("when the mode is neither tools nor classifier - then it throws and names EXTRACTION_MODE")
        void whenModeIsNeitherToolsNorClassifier_thenThrowsNamingExtractionMode(String mode) {
            assertThatThrownBy(() -> new ExtractionProperties(mode, 4, Duration.ofSeconds(90)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("EXTRACTION_MODE");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("when the max of turns is zero or less - then it throws and names EXTRACTION_MAX_TURNS")
        void whenMaxTurnsIsZeroOrLess_thenThrowsNamingExtractionMaxTurns(int maxTurns) {
            assertThatThrownBy(() -> new ExtractionProperties("tools", maxTurns, Duration.ofSeconds(90)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("EXTRACTION_MAX_TURNS");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidTurnTimeouts")
        @DisplayName(
                "when the turn timeout is null, zero or negative - then it throws and names EXTRACTION_TURN_TIMEOUT")
        void whenTurnTimeoutIsNullOrNotPositive_thenThrowsNamingExtractionTurnTimeout(
                String name, Duration turnTimeout) {
            assertThatThrownBy(() -> new ExtractionProperties("tools", 4, turnTimeout))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("EXTRACTION_TURN_TIMEOUT");
        }

        static Stream<Arguments> invalidTurnTimeouts() {
            return Stream.of(
                    Arguments.of("null", null),
                    Arguments.of("zero", Duration.ZERO),
                    Arguments.of("negative", Duration.ofSeconds(-1)));
        }

        @Test
        @DisplayName("when the mode is null - then the mode is the tool-calling mode")
        void whenModeIsNull_thenModeIsToolCalling() {
            assertThat(new ExtractionProperties(null, 4, Duration.ofSeconds(90)).mode())
                    .isEqualTo("tools");
        }
    }
}
