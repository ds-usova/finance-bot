package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentExtractionCommandTest {

    private static final String TEXT = "spent 15 euros on lunch";
    private static final List<String> KNOWN_CATEGORIES = List.of("Food", "Travel", "Other");

    @Nested
    @DisplayName("constructing the command")
    class Construction {

        @Test
        @DisplayName("when non-blank text and a category list are given - then the command is created and "
                + "exposes both")
        void whenNonBlankTextAndCategoryList_thenCommandExposesBoth() {
            IntentExtractionCommand command = new IntentExtractionCommand(TEXT, KNOWN_CATEGORIES, Optional.empty());

            assertThat(command.text()).isEqualTo(TEXT);
            assertThat(command.knownCategories()).containsExactlyElementsOf(KNOWN_CATEGORIES);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when text is null, empty, or whitespace-only - then throws InvalidValueException")
        void whenTextIsNullEmptyOrBlank_thenThrowsInvalidValueException(String text) {
            assertThatThrownBy(() -> new IntentExtractionCommand(text, KNOWN_CATEGORIES, Optional.empty()))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the category list is empty - then throws InvalidValueException")
        void whenCategoryListIsEmpty_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new IntentExtractionCommand(TEXT, List.of(), Optional.empty()))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the category list is null - then throws InvalidValueException")
        void whenCategoryListIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new IntentExtractionCommand(TEXT, null, Optional.empty()))
                    .isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when the category list contains a null or blank element - then throws InvalidValueException")
        void whenCategoryListContainsNullOrBlankElement_thenThrowsInvalidValueException(String element) {
            List<String> categories = Arrays.asList("Food", element, "Other");

            assertThatThrownBy(() -> new IntentExtractionCommand(TEXT, categories, Optional.empty()))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when a mutable category list used to construct the command is modified afterwards - then "
                + "the command's list is unchanged, and attempting to modify the command's own list throws")
        void whenMutableCategoryListModifiedAfterConstruction_thenCommandListUnchangedAndOwnListImmutable() {
            List<String> mutable = new ArrayList<>(List.of("Food", "Travel"));

            IntentExtractionCommand command = new IntentExtractionCommand(TEXT, mutable, Optional.empty());
            mutable.add("Other");

            assertThat(command.knownCategories()).containsExactly("Food", "Travel");
            assertThatThrownBy(() -> command.knownCategories().add("Other"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("when a present default currency is given - then the command exposes it as a CurrencyCode")
        void whenDefaultCurrencyPresent_thenCommandExposesCurrencyCode() {
            CurrencyCode eur = CurrencyCode.of("EUR");

            IntentExtractionCommand command = new IntentExtractionCommand(TEXT, KNOWN_CATEGORIES, Optional.of(eur));

            assertThat(command.defaultCurrency()).contains(eur);
        }

        @Test
        @DisplayName("when the defaultCurrency Optional is null - then throws InvalidValueException")
        void whenDefaultCurrencyOptionalIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new IntentExtractionCommand(TEXT, KNOWN_CATEGORIES, null))
                    .isInstanceOf(InvalidValueException.class);
        }

    }

}
