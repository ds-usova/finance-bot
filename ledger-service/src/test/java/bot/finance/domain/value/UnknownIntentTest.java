package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidIntentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UnknownIntentTest {

    @Nested
    @DisplayName("constructing an unknown intent")
    class UnknownIntentConstructor {

        @Test
        @DisplayName("when the reason is \"no amount was stated\" - then the record holds that reason")
        void whenReasonIsNoAmountWasStated_thenRecordHoldsThatReason() {
            UnknownIntent intent = new UnknownIntent("no amount was stated");

            assertThat(intent.reason()).isEqualTo("no amount was stated");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" "})
        @DisplayName("when the reason is null or blank - then throws InvalidIntentException")
        void whenReasonIsNullOrBlank_thenThrowsInvalidIntentException(String reason) {
            assertThatThrownBy(() -> new UnknownIntent(reason)).isInstanceOf(InvalidIntentException.class);
        }
    }
}
