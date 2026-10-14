package bot.finance.ai.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.MessageIdentity;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExtractIntentsCommandTest {

    private static final String TEXT = "spent 15 euros on lunch";
    private static final List<String> CATEGORY_GROUPINGS = List.of("Food", "Travel", "Other");
    private static final List<CatalogueEntry> CATALOGUE = catalogueOf(CATEGORY_GROUPINGS);
    private static final String CATCH_ALL_GROUPING = "Other";
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 5);

    private static List<CatalogueEntry> catalogueOf(List<String> groupings) {
        if (groupings == null || groupings.stream().anyMatch(grouping -> grouping == null || grouping.isBlank())) {
            return List.of();
        }
        return groupings.stream()
                .map(grouping -> new CatalogueEntry(grouping, List.of(grouping)))
                .toList();
    }

    @Nested
    @DisplayName("constructing the command")
    class Construction {

        @Test
        @DisplayName("when a text, groupings, a catch-all and an empty currency are given - then the command "
                + "exposes each unchanged")
        void whenNonBlankTextAndCategoryList_thenCommandExposesEachComponentUnchanged() {
            ExtractIntentsCommand command = new ExtractIntentsCommand(
                    TEXT,
                    CATEGORY_GROUPINGS,
                    CATCH_ALL_GROUPING,
                    Optional.empty(),
                    CURRENT_DATE,
                    Optional.empty(),
                    CATALOGUE);

            assertThat(command.text()).isEqualTo(TEXT);
            assertThat(command.categoryGroupings()).containsExactlyElementsOf(CATEGORY_GROUPINGS);
            assertThat(command.catchAllGrouping()).isEqualTo(CATCH_ALL_GROUPING);
            assertThat(command.defaultCurrency()).isEmpty();
        }

        @Test
        @DisplayName("when the command's category groupings list is modified - then it throws "
                + "UnsupportedOperationException")
        void whenCommandsCategoryGroupingsModified_thenThrowsUnsupportedOperationException() {
            ExtractIntentsCommand command = new ExtractIntentsCommand(
                    TEXT,
                    CATEGORY_GROUPINGS,
                    CATCH_ALL_GROUPING,
                    Optional.empty(),
                    CURRENT_DATE,
                    Optional.empty(),
                    CATALOGUE);

            assertThatThrownBy(() -> command.categoryGroupings().add("New"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("when text is null, empty, or whitespace-only - then throws InvalidValueException")
        void whenTextIsNullEmptyOrBlank_thenThrowsInvalidValueException(String text) {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            text,
                            CATEGORY_GROUPINGS,
                            CATCH_ALL_GROUPING,
                            Optional.empty(),
                            CURRENT_DATE,
                            Optional.empty(),
                            CATALOGUE))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the category groupings list is empty - then throws InvalidValueException")
        void whenCategoryListIsEmpty_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            TEXT,
                            List.of(),
                            CATCH_ALL_GROUPING,
                            Optional.empty(),
                            CURRENT_DATE,
                            Optional.empty(),
                            List.of()))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the category groupings list is null - then throws InvalidValueException")
        void whenCategoryListIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            TEXT,
                            null,
                            CATCH_ALL_GROUPING,
                            Optional.empty(),
                            CURRENT_DATE,
                            Optional.empty(),
                            List.of()))
                    .isInstanceOf(InvalidValueException.class);
        }

        @ParameterizedTest
        @MethodSource("categoryGroupingsWithInvalidElement")
        @DisplayName("when the category groupings list contains a null or blank element - then throws "
                + "InvalidValueException")
        void whenCategoryListContainsNullElement_thenThrowsInvalidValueException(List<String> categoryGroupings) {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            TEXT,
                            categoryGroupings,
                            CATCH_ALL_GROUPING,
                            Optional.empty(),
                            CURRENT_DATE,
                            Optional.empty(),
                            catalogueOf(categoryGroupings)))
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
                            TEXT,
                            CATEGORY_GROUPINGS,
                            catchAllGrouping,
                            Optional.empty(),
                            CURRENT_DATE,
                            Optional.empty(),
                            CATALOGUE))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the list used to construct the command is modified afterwards - then the command's own "
                + "list is unchanged")
        void whenMutableCategoryListModifiedAfterConstruction_thenCommandListUnchanged() {
            List<String> mutable = new ArrayList<>(List.of("Food", "Travel"));

            ExtractIntentsCommand command = new ExtractIntentsCommand(
                    TEXT,
                    mutable,
                    "Food",
                    Optional.empty(),
                    CURRENT_DATE,
                    Optional.empty(),
                    catalogueOf(List.of("Food", "Travel")));
            mutable.add("Other");

            assertThat(command.categoryGroupings()).containsExactly("Food", "Travel");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidCatalogues")
        @DisplayName("when the catalogue does not match the category groupings one entry each, in order - then "
                + "throws InvalidValueException")
        void whenCatalogueDoesNotMatchGroupings_thenThrowsInvalidValueException(
                String name, List<CatalogueEntry> catalogue) {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            TEXT,
                            CATEGORY_GROUPINGS,
                            CATCH_ALL_GROUPING,
                            Optional.empty(),
                            CURRENT_DATE,
                            Optional.empty(),
                            catalogue))
                    .isInstanceOf(InvalidValueException.class);
        }

        private static Stream<Arguments> invalidCatalogues() {
            return Stream.of(
                    Arguments.of("no entry for the catch-all grouping", catalogueOf(List.of("Food", "Travel"))),
                    Arguments.of(
                            "a null entry",
                            Arrays.asList(
                                    new CatalogueEntry("Food", List.of("Food")),
                                    null,
                                    new CatalogueEntry("Other", List.of("Other")))),
                    Arguments.of("a grouping named twice", catalogueOf(List.of("Food", "Travel", "Travel", "Other"))),
                    Arguments.of(
                            "a grouping the command lacks", catalogueOf(List.of("Food", "Travel", "Other", "Pets"))),
                    Arguments.of("another order", catalogueOf(List.of("Other", "Food", "Travel"))));
        }

        private static Stream<List<String>> categoryGroupingsWithInvalidElement() {
            return Stream.of(Arrays.asList("Food", null), Arrays.asList("Food", "   "));
        }

        @Test
        @DisplayName("when a present default currency is given - then the command exposes it as a CurrencyCode")
        void whenDefaultCurrencyPresent_thenCommandExposesCurrencyCode() {
            CurrencyCode eur = CurrencyCode.of("EUR");

            ExtractIntentsCommand command = new ExtractIntentsCommand(
                    TEXT,
                    CATEGORY_GROUPINGS,
                    CATCH_ALL_GROUPING,
                    Optional.of(eur),
                    CURRENT_DATE,
                    Optional.empty(),
                    CATALOGUE);

            assertThat(command.defaultCurrency()).contains(eur);
        }

        @Test
        @DisplayName("when the defaultCurrency Optional is null - then throws InvalidValueException")
        void whenDefaultCurrencyOptionalIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            TEXT,
                            CATEGORY_GROUPINGS,
                            CATCH_ALL_GROUPING,
                            null,
                            CURRENT_DATE,
                            Optional.empty(),
                            CATALOGUE))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName("when the current date is null - then throws InvalidValueException")
        void whenCurrentDateIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            TEXT,
                            CATEGORY_GROUPINGS,
                            CATCH_ALL_GROUPING,
                            Optional.empty(),
                            null,
                            Optional.empty(),
                            CATALOGUE))
                    .isInstanceOf(InvalidValueException.class);
        }

        @Test
        @DisplayName(
                "when an otherwise valid command carries a current date - then currentDate() reads back " + "unchanged")
        void whenCommandCarriesCurrentDate_thenCurrentDateReadsBackUnchanged() {
            ExtractIntentsCommand command = new ExtractIntentsCommand(
                    TEXT,
                    CATEGORY_GROUPINGS,
                    CATCH_ALL_GROUPING,
                    Optional.empty(),
                    CURRENT_DATE,
                    Optional.empty(),
                    CATALOGUE);

            assertThat(command.currentDate()).isEqualTo(CURRENT_DATE);
        }

        @Test
        @DisplayName("when a present message identity is given - then messageIdentity() reads it back unchanged")
        void whenMessageIdentityPresent_thenMessageIdentityReadsBackUnchanged() {
            MessageIdentity messageIdentity = new MessageIdentity(42L, "incoming-id");

            ExtractIntentsCommand command = new ExtractIntentsCommand(
                    TEXT,
                    CATEGORY_GROUPINGS,
                    CATCH_ALL_GROUPING,
                    Optional.empty(),
                    CURRENT_DATE,
                    Optional.of(messageIdentity),
                    CATALOGUE);

            assertThat(command.messageIdentity()).contains(messageIdentity);
        }

        @Test
        @DisplayName("when the message identity is empty - then the command is valid and messageIdentity() is empty")
        void whenMessageIdentityEmpty_thenCommandValidAndMessageIdentityEmpty() {
            ExtractIntentsCommand command = new ExtractIntentsCommand(
                    TEXT,
                    CATEGORY_GROUPINGS,
                    CATCH_ALL_GROUPING,
                    Optional.empty(),
                    CURRENT_DATE,
                    Optional.empty(),
                    CATALOGUE);

            assertThat(command.messageIdentity()).isEmpty();
        }

        @Test
        @DisplayName("when the messageIdentity Optional is null - then throws InvalidValueException")
        void whenMessageIdentityOptionalIsNull_thenThrowsInvalidValueException() {
            assertThatThrownBy(() -> new ExtractIntentsCommand(
                            TEXT,
                            CATEGORY_GROUPINGS,
                            CATCH_ALL_GROUPING,
                            Optional.empty(),
                            CURRENT_DATE,
                            null,
                            CATALOGUE))
                    .isInstanceOf(InvalidValueException.class);
        }
    }
}
