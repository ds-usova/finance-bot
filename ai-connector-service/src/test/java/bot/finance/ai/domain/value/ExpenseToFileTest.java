package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Optional;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExpenseToFileTest {

    private static final CurrencyCode EUR = CurrencyCode.of("EUR");

    @Nested
    @DisplayName("constructing an ExpenseToFile")
    class Construction {

        static Stream<Arguments> missingFieldScenarios() {
            return Stream.of(
                    Arguments.of(Named.of("null description", (ThrowingCallable)
                            () -> new ExpenseToFile(null, Optional.empty(), "15.00", EUR))),
                    Arguments.of(Named.of("blank description", (ThrowingCallable)
                            () -> new ExpenseToFile("  ", Optional.empty(), "15.00", EUR))),
                    Arguments.of(Named.of("null merchant Optional", (ThrowingCallable)
                            () -> new ExpenseToFile("lunch", null, "15.00", EUR))),
                    Arguments.of(Named.of("null amount", (ThrowingCallable)
                            () -> new ExpenseToFile("lunch", Optional.empty(), null, EUR))),
                    Arguments.of(Named.of("null currency", (ThrowingCallable)
                            () -> new ExpenseToFile("lunch", Optional.empty(), "15.00", null))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("missingFieldScenarios")
        @DisplayName("when a required field is null or the description is blank - then throws InvalidValueException")
        void whenRequiredFieldMissing_thenThrowsInvalidValueException(ThrowingCallable constructor) {
            assertThatThrownBy(constructor).isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-4", "1e3", "abc"})
        @DisplayName("when the amount is not a positive decimal - then throws InvalidValueException")
        void whenAmountNotPositiveDecimal_thenThrowsInvalidValueException(String amount) {
            assertThatThrownBy(() -> new ExpenseToFile("lunch", Optional.empty(), amount, EUR))
                    .isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        @DisplayName("when the merchant is present but blank - then the merchant reads absent")
        void whenMerchantPresentButBlank_thenMerchantReadsAbsent(String merchant) {
            ExpenseToFile expense = new ExpenseToFile("lunch", Optional.of(merchant), "15.00", EUR);

            assertThat(expense.merchant()).isEmpty();
        }
    }
}
