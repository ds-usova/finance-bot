package bot.finance.adapter.async;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class WrittenMessageReaderPropertiesTest {

    @Nested
    @DisplayName("WrittenMessageReaderProperties()")
    class PropertiesConstructor {

        @ParameterizedTest
        @NullSource
        @DisplayName("when the longest queue wait is null - then IllegalArgumentException names TURN_QUEUE_MAX_WAIT")
        void whenLongestQueueWaitIsNull_thenThrowsNamingTurnQueueMaxWait(Duration wait) {
            assertThatThrownBy(() ->
                            new WrittenMessageReaderProperties(Duration.ofSeconds(10), Duration.ofMinutes(5), wait))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TURN_QUEUE_MAX_WAIT");
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when the longest queue wait is zero or negative - then IllegalArgumentException names it")
        void whenLongestQueueWaitIsZeroOrNegative_thenThrowsNamingTurnQueueMaxWait(long seconds) {
            assertThatThrownBy(() -> new WrittenMessageReaderProperties(
                            Duration.ofSeconds(10), Duration.ofMinutes(5), Duration.ofSeconds(seconds)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("TURN_QUEUE_MAX_WAIT");
        }
    }
}
