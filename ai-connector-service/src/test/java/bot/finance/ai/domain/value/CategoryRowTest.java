package bot.finance.ai.domain.value;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.common.fixtures.RecordedChangeFixtures;
import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryRowTest {

    @Nested
    @DisplayName("constructing a CategoryRow")
    class CompactConstructor {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when name is null or blank - then throws InvalidValueException")
        void whenNameIsNullOrBlank_thenThrowsInvalidValueException(String name) {
            assertThatThrownBy(() -> new CategoryRow(
                            RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                            RecordedChangeFixtures.DEFAULT_USER_ID,
                            Optional.of(RecordedChangeFixtures.DEFAULT_PARENT_ID),
                            name))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when parentId Optional is null - then throws InvalidValueException")
        void whenParentIdOptionalIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new CategoryRow(
                            RecordedChangeFixtures.DEFAULT_CATEGORY_ID,
                            RecordedChangeFixtures.DEFAULT_USER_ID,
                            null,
                            "Groceries"))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
