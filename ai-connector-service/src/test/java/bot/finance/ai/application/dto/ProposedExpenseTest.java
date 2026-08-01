package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProposedExpenseTest {

    private static final Money AMOUNT = Money.of("15.00", "EUR");

    @Nested
    @DisplayName("constructing the proposed expense")
    class Construction {

        @Test
        @DisplayName("when a category name, a present parent category name, a description and a Money are given - "
                + "then every component reads back unchanged")
        void whenAllComponentsPresent_thenEveryComponentReadsBackUnchanged() {
            ProposedExpense expense = new ProposedExpense("Travel", Optional.of("Trips"), "Flight to Lisbon", AMOUNT);

            assertThat(expense.categoryName()).isEqualTo("Travel");
            assertThat(expense.parentCategoryName()).contains("Trips");
            assertThat(expense.description()).isEqualTo("Flight to Lisbon");
            assertThat(expense.amount()).isEqualTo(AMOUNT);
        }

        @Test
        @DisplayName("when the parent category name is empty and everything else is present - then the record is "
                + "accepted")
        void whenParentCategoryNameEmpty_thenRecordAccepted() {
            ProposedExpense expense = new ProposedExpense("Travel", Optional.empty(), "Flight to Lisbon", AMOUNT);

            assertThat(expense.parentCategoryName()).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("bot.finance.ai.application.dto.ProposedExpenseTest#invalidConstructorArguments")
        @DisplayName("when the category name or description is null or blank, or the amount or parent Optional is "
                + "null - then throws InvalidValueException")
        void whenAnyComponentInvalid_thenThrowsInvalidValueException(
                String categoryName, Optional<String> parentCategoryName, String description, Money amount) {
            assertThatThrownBy(() -> new ProposedExpense(categoryName, parentCategoryName, description, amount))
                    .isInstanceOf(InvalidValueException.class);
        }

    }

    static Stream<Arguments> invalidConstructorArguments() {
        return Stream.of(
                Arguments.of(null, Optional.of("Trips"), "Flight to Lisbon", AMOUNT),
                Arguments.of("", Optional.of("Trips"), "Flight to Lisbon", AMOUNT),
                Arguments.of("   ", Optional.of("Trips"), "Flight to Lisbon", AMOUNT),
                Arguments.of("Travel", null, "Flight to Lisbon", AMOUNT),
                Arguments.of("Travel", Optional.of("Trips"), null, AMOUNT),
                Arguments.of("Travel", Optional.of("Trips"), "", AMOUNT),
                Arguments.of("Travel", Optional.of("Trips"), "   ", AMOUNT),
                Arguments.of("Travel", Optional.of("Trips"), "Flight to Lisbon", null));
    }

}
