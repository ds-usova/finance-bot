package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnknownIntentTest {

    @Nested
    @DisplayName("constructing an UnknownIntent")
    class Construction {

        @Test
        @DisplayName("when a non-blank reason is given - then the intent is created")
        void whenNonBlankReasonGiven_thenIntentIsCreated() {
            UnknownIntent intent = new UnknownIntent("provider returned no usable answer");

            assertThat(intent.reason()).isEqualTo("provider returned no usable answer");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when the reason is null or blank - then throws InvalidValueException, since an unknown "
                + "result that does not say why is not useful to the caller")
        void whenReasonIsNullOrBlank_thenThrowsInvalidValueException(String reason) {
            assertThatThrownBy(() -> new UnknownIntent(reason))
                    .isInstanceOf(InvalidValueException.class);
        }

    }

}
