package bot.finance.ai.adapter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

        @Test
        @DisplayName("when the mode is null - then the mode is the tool-calling mode")
        void whenModeIsNull_thenModeIsToolCalling() {
            assertThat(new ExtractionProperties(null, 4, Duration.ofSeconds(90)).mode())
                    .isEqualTo("tools");
        }
    }
}
