package bot.finance.ai.adapter.ledger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LedgerTurnPropertiesTest {

    @Nested
    @DisplayName("LedgerTurnProperties()")
    class Binding {

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the target is null or blank - then it throws and names LEDGER_TURN_TARGET")
        void whenTargetIsNullOrBlank_thenThrowsNamingLedgerTurnTarget(String target) {
            assertThatThrownBy(() -> new LedgerTurnProperties(target, Duration.ofSeconds(5)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("LEDGER_TURN_TARGET");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"PT0S", "PT-1S"})
        @DisplayName("when the timeout is zero or less - then it throws and names LEDGER_TURN_TIMEOUT")
        void whenTimeoutIsZeroOrLess_thenThrowsNamingLedgerTurnTimeout(String timeout) {
            assertThatThrownBy(() -> new LedgerTurnProperties("localhost:9090", Duration.parse(timeout)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("LEDGER_TURN_TIMEOUT");
        }
    }
}
