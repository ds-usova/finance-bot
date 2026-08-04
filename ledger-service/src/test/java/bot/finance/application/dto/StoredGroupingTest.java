package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidGroupingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StoredGroupingTest {

    @Nested
    @DisplayName("constructing a new stored grouping")
    class StoredGroupingConstructor {

        @ParameterizedTest
        @ValueSource(longs = {0, -1, -100})
        @DisplayName("when the id is zero or negative - then throws InvalidGroupingException")
        void whenIdIsZeroOrNegative_thenThrowsInvalidGroupingException(long id) {
            assertThatThrownBy(() -> new StoredGrouping(id, "Household")).isInstanceOf(InvalidGroupingException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the name is absent, empty, or only whitespace - then throws InvalidGroupingException")
        void whenNameIsAbsentEmptyOrWhitespace_thenThrowsInvalidGroupingException(String name) {
            assertThatThrownBy(() -> new StoredGrouping(1L, name)).isInstanceOf(InvalidGroupingException.class);
        }

        @Test
        @DisplayName("when the id and name are valid - then the record carries them unchanged")
        void whenIdAndNameAreValid_thenTheRecordCarriesThemUnchanged() {
            StoredGrouping storedGrouping = new StoredGrouping(1L, "Household");

            assertThat(storedGrouping.id()).isEqualTo(1L);
            assertThat(storedGrouping.name()).isEqualTo("Household");
        }
    }
}
