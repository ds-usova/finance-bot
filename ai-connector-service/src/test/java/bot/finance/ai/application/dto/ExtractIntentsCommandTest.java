package bot.finance.ai.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExtractIntentsCommandTest {

    private static final String TEXT = "spent 15 euros on lunch";
    // TODO RU08: KNOWN_CATEGORIES named a List<KnownCategory>; KnownCategory is deleted (ST03). Replace with
    // grouping names and a catch-all.
    // private static final List<KnownCategory> KNOWN_CATEGORIES = List.of(
    //         new KnownCategory("Food", "Groceries"),
    //         new KnownCategory("Travel", "Trips"),
    //         new KnownCategory("Other", "Other"));

    @Nested
    @DisplayName("constructing the command")
    class Construction {

        // TODO RU08: replace with the grouping-list pass-through and validation scenarios; every scenario below
        // changes only its constructor arguments — grouping names and a catch-all in place of KnownCategory
        // values — and keeps the assertion it already makes.
        //
        // @Test
        // @DisplayName(
        //         "when non-blank text and a category list are given - then the command is created and " + "exposes
        // both")
        // void whenNonBlankTextAndCategoryList_thenCommandExposesBoth() {
        //     ExtractIntentsCommand command = new ExtractIntentsCommand(TEXT, KNOWN_CATEGORIES, Optional.empty());
        //
        //     assertThat(command.text()).isEqualTo(TEXT);
        //     assertThat(command.knownCategories()).containsExactlyElementsOf(KNOWN_CATEGORIES);
        // }
        //
        // @ParameterizedTest
        // @NullSource
        // @ValueSource(strings = {"", "   "})
        // @DisplayName("when text is null, empty, or whitespace-only - then throws InvalidValueException")
        // void whenTextIsNullEmptyOrBlank_thenThrowsInvalidValueException(String text) {
        //     assertThatThrownBy(() -> new ExtractIntentsCommand(text, KNOWN_CATEGORIES, Optional.empty()))
        //             .isInstanceOf(InvalidValueException.class);
        // }
        //
        // @Test
        // @DisplayName("when the category list is empty - then throws InvalidValueException")
        // void whenCategoryListIsEmpty_thenThrowsInvalidValueException() {
        //     assertThatThrownBy(() -> new ExtractIntentsCommand(TEXT, List.of(), Optional.empty()))
        //             .isInstanceOf(InvalidValueException.class);
        // }
        //
        // @Test
        // @DisplayName("when the category list is null - then throws InvalidValueException")
        // void whenCategoryListIsNull_thenThrowsInvalidValueException() {
        //     assertThatThrownBy(() -> new ExtractIntentsCommand(TEXT, null, Optional.empty()))
        //             .isInstanceOf(InvalidValueException.class);
        // }
        //
        // @Test
        // @DisplayName("when the category list contains a null element - then throws InvalidValueException")
        // void whenCategoryListContainsNullElement_thenThrowsInvalidValueException() {
        //     List<KnownCategory> categories = Arrays.asList(new KnownCategory("Food", "Groceries"), null);
        //
        //     assertThatThrownBy(() -> new ExtractIntentsCommand(TEXT, categories, Optional.empty()))
        //             .isInstanceOf(InvalidValueException.class);
        // }
        //
        // @Test
        // @DisplayName("when a mutable category list used to construct the command is modified afterwards - then "
        //         + "the command's list is unchanged, and attempting to modify the command's own list throws")
        // void whenMutableCategoryListModifiedAfterConstruction_thenCommandListUnchangedAndOwnListImmutable() {
        //     List<KnownCategory> mutable = new ArrayList<>(
        //             List.of(new KnownCategory("Food", "Groceries"), new KnownCategory("Travel", "Trips")));
        //
        //     ExtractIntentsCommand command = new ExtractIntentsCommand(TEXT, mutable, Optional.empty());
        //     mutable.add(new KnownCategory("Other", "Other"));
        //
        //     assertThat(command.knownCategories())
        //             .containsExactly(new KnownCategory("Food", "Groceries"), new KnownCategory("Travel", "Trips"));
        //     assertThatThrownBy(() -> command.knownCategories().add(new KnownCategory("Other", "Other")))
        //             .isInstanceOf(UnsupportedOperationException.class);
        // }

        @Test
        @DisplayName("when a present default currency is given - then the command exposes it as a CurrencyCode")
        void whenDefaultCurrencyPresent_thenCommandExposesCurrencyCode() {
            CurrencyCode eur = CurrencyCode.of("EUR");
            // TODO RU08: build the command from grouping names and a catch-all, not KNOWN_CATEGORIES.
            List<String> categoryGroupings = List.of("Food", "Travel", "Other");

            ExtractIntentsCommand command =
                    new ExtractIntentsCommand(TEXT, categoryGroupings, "Other", Optional.of(eur));

            assertThat(command.defaultCurrency()).contains(eur);
        }

        @Test
        @DisplayName("when the defaultCurrency Optional is null - then throws InvalidValueException")
        void whenDefaultCurrencyOptionalIsNull_thenThrowsInvalidValueException() {
            // TODO RU08: build the command from grouping names and a catch-all, not KNOWN_CATEGORIES.
            List<String> categoryGroupings = List.of("Food", "Travel", "Other");

            assertThatThrownBy(() -> new ExtractIntentsCommand(TEXT, categoryGroupings, "Other", null))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
