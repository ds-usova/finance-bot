package bot.finance.ai.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExtractIntentsCommandTest {

    private static final String TEXT = "spent 15 euros on lunch";
    private static final List<String> CATEGORY_GROUPINGS = List.of("Food", "Travel", "Other");
    private static final String CATCH_ALL_GROUPING = "Other";
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 5);

    @Nested
    @DisplayName("constructing the command")
    class Construction {

        @Test
        @DisplayName("when non-blank text, category groupings, a catch-all among them, and an empty currency are "
                + "given - then the command exposes every component unchanged and the groupings are unmodifiable")
        void whenNonBlankTextAndCategoryList_thenCommandExposesBoth() {
            ExtractIntentsCommand command = new ExtractIntentsCommand(
                    TEXT, CATEGORY_GROUPINGS, CATCH_ALL_GROUPING, Optional.empty(), CURRENT_DATE);

            assertThat(command.text()).isEqualTo(TEXT);
            assertThat(command.categoryGroupings()).containsExactlyElementsOf(CATEGORY_GROUPINGS);
            assertThat(command.catchAllGrouping()).isEqualTo(CATCH_ALL_GROUPING);
            assertThat(command.defaultCurrency()).isEmpty();
            assertThatThrownBy(() -> command.categoryGroupings().add("New"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when text is null, empty, or whitespace-only - then throws InvalidValueException")
        void whenTextIsNullEmptyOrBlank_thenThrowsInvalidValueException(String text) {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            text, CATEGORY_GROUPINGS, CATCH_ALL_GROUPING, Optional.empty(), CURRENT_DATE))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the category groupings list is empty - then throws InvalidValueException")
        void whenCategoryListIsEmpty_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            TEXT, List.of(), CATCH_ALL_GROUPING, Optional.empty(), CURRENT_DATE))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the category groupings list is null - then throws InvalidValueException")
        void whenCategoryListIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() ->
                            new ExtractIntentsCommand(TEXT, null, CATCH_ALL_GROUPING, Optional.empty(), CURRENT_DATE))
                    .isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @MethodSource("categoryGroupingsWithInvalidElement")
        @DisplayName("when the category groupings list contains a null or blank element - then throws "
                + "InvalidValueException")
        void whenCategoryListContainsNullElement_thenThrowsInvalidValueException(List<String> categoryGroupings) {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            TEXT, categoryGroupings, CATCH_ALL_GROUPING, Optional.empty(), CURRENT_DATE))
                    .isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "NotAGrouping"})
        @DisplayName("when the catch-all grouping is null, blank, or not among the category groupings - then "
                + "throws InvalidValueException")
        void whenCatchAllGroupingIsNullBlankOrNotAmongCategoryGroupings_thenThrowsInvalidValueException(
                String catchAllGrouping) {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            TEXT, CATEGORY_GROUPINGS, catchAllGrouping, Optional.empty(), CURRENT_DATE))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when a mutable category groupings list used to construct the command is modified afterwards "
                + "- then the command's list is unchanged, and attempting to modify the command's own list throws")
        void whenMutableCategoryListModifiedAfterConstruction_thenCommandListUnchangedAndOwnListImmutable() {
            List<String> mutable = new ArrayList<>(List.of("Food", "Travel"));

            ExtractIntentsCommand command =
                    new ExtractIntentsCommand(TEXT, mutable, "Food", Optional.empty(), CURRENT_DATE);
            mutable.add("Other");

            assertThat(command.categoryGroupings()).containsExactly("Food", "Travel");
            assertThatThrownBy(() -> command.categoryGroupings().add("Other"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        private static Stream<List<String>> categoryGroupingsWithInvalidElement() {
            return Stream.of(Arrays.asList("Food", null), Arrays.asList("Food", "   "));
        }

        @Test
        @DisplayName("when a present default currency is given - then the command exposes it as a CurrencyCode")
        void whenDefaultCurrencyPresent_thenCommandExposesCurrencyCode() {
            CurrencyCode eur = CurrencyCode.of("EUR");

            ExtractIntentsCommand command = new ExtractIntentsCommand(
                    TEXT, CATEGORY_GROUPINGS, CATCH_ALL_GROUPING, Optional.of(eur), CURRENT_DATE);

            assertThat(command.defaultCurrency()).contains(eur);
        }

        @Test
        @DisplayName("when the defaultCurrency Optional is null - then throws InvalidValueException")
        void whenDefaultCurrencyOptionalIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() ->
                            new ExtractIntentsCommand(TEXT, CATEGORY_GROUPINGS, CATCH_ALL_GROUPING, null, CURRENT_DATE))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
