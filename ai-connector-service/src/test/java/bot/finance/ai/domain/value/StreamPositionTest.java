package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StreamPositionTest {

    @Nested
    @DisplayName("constructing a StreamPosition")
    class CompactConstructor {

        @ParameterizedTest
        @CsvSource({"0, 0", "-1, 0", "1000, -1"})
        @DisplayName("when ms is zero or below, or seq is negative - then throws InvalidValueException")
        void whenMsIsZeroOrBelowOrSeqIsNegative_thenThrowsInvalidValueException(long ms, long seq) {
            assertThatThrownBy(() -> new StreamPosition(ms, seq)).isInstanceOf(InvalidValueException.class);
        }
    }

    @Nested
    @DisplayName("comparing two StreamPositions")
    class CompareTo {

        @Test
        @DisplayName("when ms differs and a text comparison would give the wrong order - then the lower ms is smaller")
        void whenMsDiffersAndTextComparisonWouldGiveWrongOrder_thenLowerMsIsSmaller() {
            StreamPosition earlier = new StreamPosition(999L, 0L);
            StreamPosition later = new StreamPosition(1000L, 0L);

            assertThat(earlier.compareTo(later)).isNegative();
            assertThat(later.compareTo(earlier)).isPositive();
        }

        @Test
        @DisplayName("when ms is shared and seq differs - then the lower seq is smaller")
        void whenMsIsSharedAndSeqDiffers_thenLowerSeqIsSmaller() {
            StreamPosition lower = new StreamPosition(1000L, 1L);
            StreamPosition higher = new StreamPosition(1000L, 2L);

            assertThat(lower.compareTo(higher)).isNegative();
            assertThat(higher.compareTo(lower)).isPositive();
        }

        @Test
        @DisplayName("when ms and seq are both equal - then neither is greater")
        void whenMsAndSeqAreBothEqual_thenNeitherIsGreater() {
            StreamPosition first = new StreamPosition(1000L, 1L);
            StreamPosition second = new StreamPosition(1000L, 1L);

            assertThat(first.compareTo(second)).isZero();
            assertThat(second.compareTo(first)).isZero();
        }
    }
}
