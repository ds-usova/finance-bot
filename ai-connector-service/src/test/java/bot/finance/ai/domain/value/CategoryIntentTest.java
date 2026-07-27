package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryIntentTest {

    @Nested
    @DisplayName("constructing a CategoryIntent")
    class Construction {

        @Test
        @DisplayName("when an operation and a non-blank name are given - then the intent is created")
        void whenOperationAndNonBlankNameGiven_thenIntentIsCreated() {
            CategoryIntent intent = new CategoryIntent(Operation.CREATE, "Groceries", Optional.empty());

            assertThat(intent.operation()).isEqualTo(Operation.CREATE);
            assertThat(intent.name()).isEqualTo("Groceries");
            assertThat(intent.newName()).isEmpty();
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when the name is null or blank - then throws InvalidValueException")
        void whenNameIsNullOrBlank_thenThrowsInvalidValueException(String name) {
            assertThatThrownBy(() -> new CategoryIntent(Operation.CREATE, name, Optional.empty()))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the operation is null - then throws InvalidValueException")
        void whenOperationIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new CategoryIntent(null, "Groceries", Optional.empty()))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the new name Optional is null - then throws InvalidValueException, since an absent "
                + "value is Optional.empty(), never null")
        void whenNewNameOptionalIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new CategoryIntent(Operation.CREATE, "Groceries", null))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the operation is UPDATE with no new name - then throws InvalidValueException")
        void whenOperationIsUpdateWithNoNewName_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new CategoryIntent(Operation.UPDATE, "Groceries", Optional.empty()))
                    .isInstanceOf(InvalidValueException.class);
        }

    }

}
