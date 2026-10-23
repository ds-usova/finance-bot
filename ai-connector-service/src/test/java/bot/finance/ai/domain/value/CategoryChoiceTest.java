package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CategoryChoiceTest {

    @Nested
    @DisplayName("constructing a CategoryChoice")
    class Construction {

        static Stream<Arguments> invalidScenarios() {
            return Stream.of(
                    Arguments.of(
                            Named.of("null grouping", (ThrowingCallable) () -> new CategoryChoice(null, "Coffee"))),
                    Arguments.of(
                            Named.of("blank grouping", (ThrowingCallable) () -> new CategoryChoice(" ", "Coffee"))),
                    Arguments.of(
                            Named.of("null category", (ThrowingCallable) () -> new CategoryChoice("Dining", null))),
                    Arguments.of(
                            Named.of("blank category", (ThrowingCallable) () -> new CategoryChoice("Dining", " "))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidScenarios")
        @DisplayName("when the grouping or the category is null or blank - then throws InvalidValueException")
        void whenGroupingOrCategoryNullOrBlank_thenThrowsInvalidValueException(ThrowingCallable constructor) {
            assertThatThrownBy(constructor).isInstanceOf(InvalidValueException.class);
        }
    }
}
