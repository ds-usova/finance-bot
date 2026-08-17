package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessageExampleTest {

    private static final ExampleExpense EXPENSE = new ExampleExpense(
            "lunch",
            "15.00",
            CurrencyCode.of("EUR"),
            Optional.of("Restaurants"),
            Optional.of("Dining"),
            ExampleOutcome.ACCEPTED);

    @Nested
    @DisplayName("constructing a MessageExample")
    class CompactConstructor {

        @Test
        @DisplayName("when a text and one expense are given - "
                + "then both read back unchanged, and mutating the list leaves them alone")
        void whenTextAndOneExpenseGiven_thenBothReadBackUnchangedAndMutatingListAfterwardsLeavesThemAlone() {
            List<ExampleExpense> expenses = new ArrayList<>(List.of(EXPENSE));

            MessageExample example = new MessageExample("spent 15 on lunch", expenses);
            expenses.add(EXPENSE);

            assertThat(example.text()).isEqualTo("spent 15 on lunch");
            assertThat(example.expenses()).containsExactly(EXPENSE);
        }

        static Stream<Arguments> invalidFieldScenarios() {
            return Stream.of(
                    Arguments.of(Named.of("null text", (String) null), List.of(EXPENSE)),
                    Arguments.of(Named.of("blank text", "   "), List.of(EXPENSE)),
                    Arguments.of(Named.of("null expense list", "spent 15 on lunch"), (List<ExampleExpense>) null),
                    Arguments.of(
                            Named.of("empty expense list", "spent 15 on lunch"), Collections.<ExampleExpense>emptyList()),
                    Arguments.of(
                            Named.of("expense list holding a null", "spent 15 on lunch"),
                            Arrays.asList(EXPENSE, null)));
        }

        @ParameterizedTest
        @MethodSource("invalidFieldScenarios")
        @DisplayName("when text is null or blank, or the expense list is null, empty, or holds a null - "
                + "then throws InvalidValueException")
        void whenAFieldIsInvalid_thenThrowsInvalidValueException(String text, List<ExampleExpense> expenses) {
            assertThatThrownBy(() -> new MessageExample(text, expenses)).isInstanceOf(InvalidValueException.class);
        }
    }
}
