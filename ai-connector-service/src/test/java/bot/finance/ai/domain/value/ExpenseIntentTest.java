package bot.finance.ai.domain.value;

import bot.finance.ai.common.IntentFixtures;
import bot.finance.ai.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpenseIntentTest {

    @Nested
    @DisplayName("constructing an expense intent")
    class Construction {

        @Test
        @DisplayName("when an operation and all optional fields are present - then the intent is created")
        void whenOperationAndAllOptionalFieldsPresent_thenIntentIsCreated() {
            ExpenseIntent intent = IntentFixtures.expenseIntent(Operation.CREATE);

            assertThat(intent.operation()).isEqualTo(Operation.CREATE);
            assertThat(intent.categoryName()).contains("Food");
            assertThat(intent.amount()).isPresent();
            assertThat(intent.description()).contains("lunch");
        }

        @ParameterizedTest
        @EnumSource(value = Operation.class, names = {"READ", "DELETE"})
        @DisplayName("when operation READ or DELETE and every optional field is empty - then the intent is created")
        void whenOperationReadOrDeleteAndEveryOptionalFieldEmpty_thenIntentIsCreated(Operation operation) {
            ExpenseIntent intent = IntentFixtures.expenseIntent(operation);

            assertThat(intent.operation()).isEqualTo(operation);
            assertThat(intent.categoryName()).isEmpty();
            assertThat(intent.amount()).isEmpty();
            assertThat(intent.description()).isEmpty();
        }

        @Test
        @DisplayName("when the operation is null - then throws InvalidValueException")
        void whenOperationIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new ExpenseIntent(
                            null, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()))
                    .isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @MethodSource("nullOptionalPositions")
        @DisplayName("when a null Optional is given in any optional position - then throws InvalidValueException")
        void whenNullOptionalGivenInAnyOptionalPosition_thenThrowsInvalidValueException(
                Optional<String> categoryName,
                Optional<Money> amount,
                Optional<String> description,
                Optional<String> parentCategoryName) {
            assertThatThrownBy(() -> new ExpenseIntent(
                            Operation.READ, categoryName, amount, description, parentCategoryName))
                    .isInstanceOf(InvalidValueException.class);
        }

        static Stream<Arguments> nullOptionalPositions() {
            return Stream.of(
                    Arguments.of(null, Optional.empty(), Optional.empty(), Optional.empty()),
                    Arguments.of(Optional.empty(), null, Optional.empty(), Optional.empty()),
                    Arguments.of(Optional.empty(), Optional.empty(), null, Optional.empty()),
                    Arguments.of(Optional.empty(), Optional.empty(), Optional.empty(), null));
        }

        @Test
        @DisplayName("when operation CREATE has no amount - then throws InvalidValueException naming the "
                + "operation and the field")
        void whenOperationCreateHasNoAmount_thenThrowsInvalidValueExceptionNamingOperationAndField() {
            assertThatThrownBy(() -> new ExpenseIntent(
                            Operation.CREATE,
                            Optional.of("Food"),
                            Optional.empty(),
                            Optional.of("lunch"),
                            Optional.empty()))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining(Operation.CREATE.name())
                    .hasMessageContaining("amount");
        }

        @Test
        @DisplayName("when operation CREATE has no category - then throws InvalidValueException naming the "
                + "operation and the field")
        void whenOperationCreateHasNoCategory_thenThrowsInvalidValueExceptionNamingOperationAndField() {
            assertThatThrownBy(() -> new ExpenseIntent(
                            Operation.CREATE,
                            Optional.empty(),
                            Optional.of(IntentFixtures.money()),
                            Optional.of("lunch"),
                            Optional.empty()))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining(Operation.CREATE.name())
                    .hasMessageContaining("categoryName");
        }

        @Test
        @DisplayName("when operation CREATE has an amount, a category, a description and a present parent "
                + "category name - then parentCategoryName reads back with that value")
        void whenOperationCreateHasPresentParentCategoryName_thenParentCategoryNameReadsBack() {
            ExpenseIntent intent = IntentFixtures.expenseIntentWithParentAndDescription("Food", "Groceries", "lunch");

            assertThat(intent.parentCategoryName()).contains("Groceries");
        }

        @Test
        @DisplayName("when operation CREATE has an amount, a category and a description but an empty parent "
                + "category name - then the intent is created")
        void whenOperationCreateHasEmptyParentCategoryName_thenIntentIsCreated() {
            ExpenseIntent intent = IntentFixtures.expenseIntent(Operation.CREATE);

            assertThat(intent.parentCategoryName()).isEmpty();
        }

        @Test
        @DisplayName("when operation CREATE has an amount and a category but an empty description - then throws "
                + "InvalidValueException naming the description")
        void whenOperationCreateHasNoDescription_thenThrowsInvalidValueExceptionNamingDescription() {
            assertThatThrownBy(() -> new ExpenseIntent(
                            Operation.CREATE,
                            Optional.of("Food"),
                            Optional.of(IntentFixtures.money()),
                            Optional.empty(),
                            Optional.empty()))
                    .isInstanceOf(InvalidValueException.class)
                    .hasMessageContaining(Operation.CREATE.name())
                    .hasMessageContaining("description");
        }

    }

}
