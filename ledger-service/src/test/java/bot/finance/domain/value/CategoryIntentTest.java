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
import org.junit.jupiter.params.provider.MethodSource;

class CategoryIntentTest {

    @Nested
    @DisplayName("constructing a category intent")
    class CategoryIntentConstructor {

        @Test
        @DisplayName("when the operation is CREATE, the name is Coffee, and there is no new name"
                + " - then it holds the operation, the name, and an empty new name")
        void whenOperationIsCreateAndNoNewName_thenHoldsOperationNameAndEmptyNewName() {
            CategoryIntent intent = new CategoryIntent(Operation.CREATE, "Coffee", Optional.empty());

            assertThat(intent.operation()).isEqualTo(Operation.CREATE);
            assertThat(intent.name()).isEqualTo("Coffee");
            assertThat(intent.newName()).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("invalidConstructorArguments")
        @DisplayName("when the operation is null, the name is null or blank, or the new-name Optional is null"
                + " - then throws InvalidIntentException")
        void whenOperationIsNullNameIsNullOrBlankOrNewNameOptionalIsNull_thenThrowsInvalidIntentException(
                Operation operation, String name, Optional<String> newName) {
            assertThatThrownBy(() -> new CategoryIntent(operation, name, newName))
                    .isInstanceOf(InvalidIntentException.class);
        }

        @Test
        @DisplayName(
                "when the operation is UPDATE and the new name is empty - then throws InvalidIntentException saying a new name is required")
        void whenOperationIsUpdateAndNewNameIsEmpty_thenThrowsInvalidIntentExceptionSayingNewNameIsRequired() {
            assertThatThrownBy(() -> new CategoryIntent(Operation.UPDATE, "Coffee", Optional.empty()))
                    .isInstanceOf(InvalidIntentException.class)
                    .hasMessageContaining("new name");
        }

        @Test
        @DisplayName("when the operation is UPDATE and the new name is Cafés - then the record holds that new name")
        void whenOperationIsUpdateAndNewNameIsCafes_thenHoldsThatNewName() {
            CategoryIntent intent = new CategoryIntent(Operation.UPDATE, "Coffee", Optional.of("Cafés"));

            assertThat(intent.newName()).contains("Cafés");
        }

        private static Stream<Arguments> invalidConstructorArguments() {
            return Stream.of(
                    Arguments.of(null, "Coffee", Optional.empty()),
                    Arguments.of(Operation.CREATE, null, Optional.empty()),
                    Arguments.of(Operation.CREATE, "   ", Optional.empty()),
                    Arguments.of(Operation.CREATE, "Coffee", null));
        }
    }
}
