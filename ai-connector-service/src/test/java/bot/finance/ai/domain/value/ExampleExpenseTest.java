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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExampleExpenseTest {

    private static final CurrencyCode EUR = CurrencyCode.of("EUR");

    private static ExampleExpense expense(
            String description,
            String amount,
            CurrencyCode currency,
            Optional<String> categoryName,
            Optional<String> groupingName,
            ExampleOutcome outcome) {
        return new ExampleExpense(description, amount, currency, categoryName, groupingName, outcome);
    }

    private static ExampleExpense validExpense() {
        return expense(
                "lunch", "15.00", EUR, Optional.of("Restaurants"), Optional.of("Dining"), ExampleOutcome.ACCEPTED);
    }

    @Nested
    @DisplayName("constructing an ExampleExpense")
    class CompactConstructor {

        @Test
        @DisplayName("when every component is given - then every component reads back unchanged")
        void whenEveryComponentGiven_thenEveryComponentReadsBackUnchanged() {
            ExampleExpense expense = expense(
                    "coffee", "3.50", EUR, Optional.of("Coffee"), Optional.of("Dining"), ExampleOutcome.DISCARDED);

            assertThat(expense.description()).isEqualTo("coffee");
            assertThat(expense.amount()).isEqualTo("3.50");
            assertThat(expense.currency()).isEqualTo(EUR);
            assertThat(expense.categoryName()).contains("Coffee");
            assertThat(expense.groupingName()).contains("Dining");
            assertThat(expense.outcome()).isEqualTo(ExampleOutcome.DISCARDED);
        }

        @Test
        @DisplayName("when the category name and the grouping name are absent - then it is accepted, both absent")
        void whenCategoryNameAndGroupingNameAreAbsent_thenItIsAcceptedBothAbsent() {
            ExampleExpense expense =
                    expense("lunch", "15.00", EUR, Optional.empty(), Optional.empty(), ExampleOutcome.ACCEPTED);

            assertThat(expense.categoryName()).isEmpty();
            assertThat(expense.groupingName()).isEmpty();
        }

        static Stream<Arguments> invalidFieldScenarios() {
            ExampleExpense valid = validExpense();
            return Stream.of(
                    Arguments.of(Named.of("null description", (ThrowingCallable) () -> expense(
                            null,
                            valid.amount(),
                            valid.currency(),
                            valid.categoryName(),
                            valid.groupingName(),
                            valid.outcome()))),
                    Arguments.of(Named.of("blank description", (ThrowingCallable) () -> expense(
                            "   ",
                            valid.amount(),
                            valid.currency(),
                            valid.categoryName(),
                            valid.groupingName(),
                            valid.outcome()))),
                    Arguments.of(Named.of("null amount", (ThrowingCallable) () -> expense(
                            valid.description(),
                            null,
                            valid.currency(),
                            valid.categoryName(),
                            valid.groupingName(),
                            valid.outcome()))),
                    Arguments.of(Named.of("blank amount", (ThrowingCallable) () -> expense(
                            valid.description(),
                            "   ",
                            valid.currency(),
                            valid.categoryName(),
                            valid.groupingName(),
                            valid.outcome()))),
                    Arguments.of(Named.of("null currency", (ThrowingCallable) () -> expense(
                            valid.description(),
                            valid.amount(),
                            null,
                            valid.categoryName(),
                            valid.groupingName(),
                            valid.outcome()))),
                    Arguments.of(Named.of("null outcome", (ThrowingCallable) () -> expense(
                            valid.description(),
                            valid.amount(),
                            valid.currency(),
                            valid.categoryName(),
                            valid.groupingName(),
                            null))),
                    Arguments.of(Named.of("null categoryName Optional", (ThrowingCallable) () -> expense(
                            valid.description(),
                            valid.amount(),
                            valid.currency(),
                            null,
                            valid.groupingName(),
                            valid.outcome()))),
                    Arguments.of(Named.of("null groupingName Optional", (ThrowingCallable) () -> expense(
                            valid.description(),
                            valid.amount(),
                            valid.currency(),
                            valid.categoryName(),
                            null,
                            valid.outcome()))));
        }

        @ParameterizedTest
        @MethodSource("invalidFieldScenarios")
        @DisplayName("when a required field is null, blank, or an Optional is null - then throws InvalidValueException")
        void whenAFieldIsInvalid_thenThrowsInvalidValueException(ThrowingCallable constructor) {
            assertThatThrownBy(constructor).isInstanceOf(InvalidValueException.class);
        }
    }
}
