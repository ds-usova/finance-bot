package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidIntentException;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ExpenseIntentTest {

    @Nested
    @DisplayName("constructing an expense intent")
    class ExpenseIntentConstructor {

        @Test
        @DisplayName(
                "when the operation is CREATE with a category, an amount and a description - then the record holds all four")
        void whenOperationIsCreateWithCategoryAmountAndDescription_thenRecordHoldsAllFour() {
            Money amount = new Money(1250, new CurrencyCode("EUR"));

            ExpenseIntent intent = new ExpenseIntent(
                    Operation.CREATE, Optional.of("Dining"), Optional.of(amount), Optional.of("Dinner with friends"));

            assertThat(intent.operation()).isEqualTo(Operation.CREATE);
            assertThat(intent.categoryName()).contains("Dining");
            assertThat(intent.amount()).contains(amount);
            assertThat(intent.description()).contains("Dinner with friends");
        }

        @ParameterizedTest
        @MethodSource("nullOperationOrOptionalComponent")
        @DisplayName("when the operation or any Optional component is null - then throws InvalidIntentException")
        void whenOperationOrAnyOptionalComponentIsNull_thenThrowsInvalidIntentException(
                Operation operation,
                Optional<String> categoryName,
                Optional<Money> amount,
                Optional<String> description) {
            assertThatThrownBy(() -> new ExpenseIntent(operation, categoryName, amount, description))
                    .isInstanceOf(InvalidIntentException.class);
        }

        static Stream<Arguments> nullOperationOrOptionalComponent() {
            return Stream.of(
                    Arguments.of(null, Optional.empty(), Optional.empty(), Optional.empty()),
                    Arguments.of(Operation.READ, null, Optional.empty(), Optional.empty()),
                    Arguments.of(Operation.READ, Optional.empty(), null, Optional.empty()),
                    Arguments.of(Operation.READ, Optional.empty(), Optional.empty(), null));
        }

        @Test
        @DisplayName("when the operation is CREATE with no amount - then throws InvalidIntentException naming "
                + "CREATE and the amount")
        void whenOperationIsCreateWithNoAmount_thenThrowsInvalidIntentExceptionNamingCreateAndAmount() {
            assertThatThrownBy(() -> new ExpenseIntent(
                            Operation.CREATE, Optional.of("Dining"), Optional.empty(), Optional.empty()))
                    .isInstanceOf(InvalidIntentException.class)
                    .hasMessageContaining(Operation.CREATE.name())
                    .hasMessageContaining("amount");
        }

        @Test
        @DisplayName("when the operation is CREATE with an amount but no category name - then throws "
                + "InvalidIntentException naming CREATE and the category")
        void whenOperationIsCreateWithAmountButNoCategoryName_thenThrowsInvalidIntentExceptionNamingCreateAndCategory() {
            Money amount = new Money(1250, new CurrencyCode("EUR"));

            assertThatThrownBy(() -> new ExpenseIntent(
                            Operation.CREATE, Optional.empty(), Optional.of(amount), Optional.empty()))
                    .isInstanceOf(InvalidIntentException.class)
                    .hasMessageContaining(Operation.CREATE.name())
                    .hasMessageContaining("category");
        }

        @ParameterizedTest
        @EnumSource(
                value = Operation.class,
                names = {"READ", "UPDATE", "DELETE"})
        @DisplayName("when the operation is READ, UPDATE or DELETE with every optional empty - then the record is "
                + "accepted")
        void whenOperationIsReadUpdateOrDeleteWithEveryOptionalEmpty_thenRecordIsAccepted(Operation operation) {
            ExpenseIntent intent = new ExpenseIntent(operation, Optional.empty(), Optional.empty(), Optional.empty());

            assertThat(intent.operation()).isEqualTo(operation);
            assertThat(intent.categoryName()).isEmpty();
            assertThat(intent.amount()).isEmpty();
            assertThat(intent.description()).isEmpty();
        }
    }
}
