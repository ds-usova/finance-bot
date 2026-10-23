package bot.finance.application.dto;

import static bot.finance.common.fixtures.IncomingMessages.newIncomingMessageId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
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
import org.junit.jupiter.params.provider.ValueSource;

class IntentExtractionRequestTest {

    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 5);

    private static List<CatalogueEntry> catalogue(String... groupings) {
        return Arrays.stream(groupings)
                .map(grouping -> new CatalogueEntry(grouping, List.of("category")))
                .toList();
    }

    private static IntentExtractionRequest requestWithTwoGroupings() {
        return new IntentExtractionRequest(
                "lunch 12 euro",
                catalogue("groceries", "transport"),
                "transport",
                Optional.empty(),
                1L,
                newIncomingMessageId(),
                CURRENT_DATE);
    }

    @Nested
    @DisplayName("constructing an intent extraction request")
    class IntentExtractionRequestConstructor {

        @Test
        @DisplayName("when text, one grouping, and a default currency are valid - then it holds all three")
        void whenTextOneCategoryAndDefaultCurrencyAreValid_thenItHoldsAllThree() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    catalogue("groceries"),
                    "groceries",
                    Optional.of(CurrencyCode.of("EUR")),
                    1L,
                    newIncomingMessageId(),
                    CURRENT_DATE);

            assertThat(request.text()).isEqualTo("lunch 12 euro");
            assertThat(request.categoryGroupings()).containsExactly("groceries");
            assertThat(request.defaultCurrency()).contains(CurrencyCode.of("EUR"));
        }

        @Test
        @DisplayName("when text, two groupings, an empty currency and a userId are valid - then every "
                + "component reads back unchanged")
        void whenEveryComponentIsValid_thenEveryComponentReadsBackUnchanged() {
            IntentExtractionRequest request = requestWithTwoGroupings();

            assertThat(request.text()).isEqualTo("lunch 12 euro");
            assertThat(request.categoryGroupings()).containsExactly("groceries", "transport");
            assertThat(request.catchAllGrouping()).isEqualTo("transport");
            assertThat(request.defaultCurrency()).isEmpty();
            assertThat(request.userId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("when the request is built from a list of groupings - then categoryGroupings() is unmodifiable")
        void whenRequestIsBuiltFromAListOfGroupings_thenCategoryGroupingsIsUnmodifiable() {
            IntentExtractionRequest request = requestWithTwoGroupings();

            assertThatThrownBy(() -> request.categoryGroupings().add("utilities"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @ParameterizedTest(name = "text={0}")
        @MethodSource("nullOrBlankText")
        @DisplayName("when text is null or blank - then throws InvalidExtractionRequestException")
        void whenTextIsNullOrBlank_thenThrowsInvalidExtractionRequestException(String text) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            text,
                            catalogue("groceries"),
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            1L,
                            newIncomingMessageId(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrBlankText() {
            return Stream.of(arguments((Object) null), arguments("   "));
        }

        @ParameterizedTest(name = "catalogue={0}")
        @MethodSource("nullOrEmptyCatalogue")
        @DisplayName("when category groupings is null or empty - then throws InvalidExtractionRequestException")
        void whenCategoryGroupingsIsNullOrEmpty_thenThrowsInvalidExtractionRequestException(
                List<CatalogueEntry> catalogue) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            catalogue,
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            1L,
                            newIncomingMessageId(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrEmptyCatalogue() {
            return Stream.of(arguments((Object) null), arguments(List.of()));
        }

        @ParameterizedTest(name = "catalogue={0}")
        @MethodSource("catalogueWithNullOrBlankGrouping")
        @DisplayName("when category groupings carries a null or blank element - then throws "
                + "InvalidExtractionRequestException")
        void whenCategoryGroupingsContainsNullOrBlankElement_thenThrowsInvalidExtractionRequestException(
                List<CatalogueEntry> catalogue) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            catalogue,
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            1L,
                            newIncomingMessageId(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> catalogueWithNullOrBlankGrouping() {
            return Stream.of(arguments((Object) catalogue("groceries", null)), arguments((Object)
                    catalogue("groceries", "   ")));
        }

        @ParameterizedTest(name = "catchAllGrouping={0}")
        @MethodSource("nullOrBlankCatchAllGrouping")
        @DisplayName("when the catch-all grouping is null or blank - then throws InvalidExtractionRequestException")
        void whenCatchAllGroupingIsNullOrBlank_thenThrowsInvalidExtractionRequestException(String catchAllGrouping) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            catalogue("groceries"),
                            catchAllGrouping,
                            Optional.of(CurrencyCode.of("EUR")),
                            1L,
                            newIncomingMessageId(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrBlankCatchAllGrouping() {
            return Stream.of(arguments((Object) null), arguments("   "));
        }

        @Test
        @DisplayName("when the catch-all grouping is not one of the category groupings - then throws "
                + "InvalidExtractionRequestException")
        void whenCatchAllGroupingIsNotOneOfCategoryGroupings_thenThrowsInvalidExtractionRequestException() {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            catalogue("groceries", "transport"),
                            "utilities",
                            Optional.of(CurrencyCode.of("EUR")),
                            1L,
                            newIncomingMessageId(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @Test
        @DisplayName("when the default currency optional is null - then throws InvalidExtractionRequestException")
        void whenDefaultCurrencyIsNull_thenThrowsInvalidExtractionRequestException() {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            catalogue("groceries"),
                            "groceries",
                            null,
                            1L,
                            newIncomingMessageId(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @Test
        @DisplayName("when the default currency is empty - then default currency comes back empty")
        void whenDefaultCurrencyIsEmpty_thenDefaultCurrencyComesBackEmpty() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    catalogue("groceries"),
                    "groceries",
                    Optional.empty(),
                    1L,
                    newIncomingMessageId(),
                    CURRENT_DATE);

            assertThat(request.defaultCurrency()).isEmpty();
        }

        @ParameterizedTest(name = "userId={0}")
        @ValueSource(longs = {0L, -1L})
        @DisplayName("when userId is zero or negative - then throws InvalidExtractionRequestException")
        void whenUserIdIsZeroOrNegative_thenThrowsInvalidExtractionRequestException(long userId) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            catalogue("groceries"),
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            userId,
                            newIncomingMessageId(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @ParameterizedTest(name = "catalogue={0}")
        @MethodSource("catalogueWithInvalidEntry")
        @DisplayName(
                "when a catalogue entry is null or has invalid categories - then throws InvalidExtractionRequestException")
        void whenCatalogueHoldsInvalidEntry_thenThrowsInvalidExtractionRequestException(
                List<CatalogueEntry> catalogue) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            catalogue,
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            1L,
                            newIncomingMessageId(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> catalogueWithInvalidEntry() {
            CatalogueEntry valid = new CatalogueEntry("groceries", List.of("Milk"));
            return Stream.of(
                    arguments((Object) Arrays.asList(valid, null)),
                    arguments((Object) List.of(valid, new CatalogueEntry("transport", null))),
                    arguments((Object) List.of(valid, new CatalogueEntry("transport", List.of()))),
                    arguments((Object) List.of(valid, new CatalogueEntry("transport", Arrays.asList("Bus", null)))),
                    arguments((Object) List.of(valid, new CatalogueEntry("transport", List.of("Bus", "   ")))));
        }

        @Test
        @DisplayName(
                "when the passed catalogue is changed afterwards - then catalogue() keeps the original entries and is unmodifiable")
        void whenCatalogueIsChangedAfterConstruction_thenCatalogueHoldsEntriesAsAtConstructionAndIsUnmodifiable() {
            List<CatalogueEntry> mutableCatalogue = new ArrayList<>(List.of(
                    new CatalogueEntry("groceries", List.of("Milk", "Bread")),
                    new CatalogueEntry("transport", List.of("Bus"))));

            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    mutableCatalogue,
                    "transport",
                    Optional.empty(),
                    1L,
                    newIncomingMessageId(),
                    CURRENT_DATE);
            mutableCatalogue.add(new CatalogueEntry("utilities", List.of("Water")));
            mutableCatalogue.remove(0);

            assertThat(request.catalogue())
                    .containsExactly(
                            new CatalogueEntry("groceries", List.of("Milk", "Bread")),
                            new CatalogueEntry("transport", List.of("Bus")));
            assertThatThrownBy(() -> request.catalogue().add(new CatalogueEntry("utilities", List.of("Water"))))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("when the mutable grouping list handed to the constructor is modified afterwards - "
                + "then category groupings is unchanged")
        void whenGroupingListIsModifiedAfterConstruction_thenCategoryGroupingsIsUnchanged() {
            List<CatalogueEntry> mutableCatalogue = new ArrayList<>(catalogue("groceries"));

            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    mutableCatalogue,
                    "groceries",
                    Optional.of(CurrencyCode.of("EUR")),
                    1L,
                    newIncomingMessageId(),
                    CURRENT_DATE);
            mutableCatalogue.addAll(catalogue("transport"));

            assertThat(request.categoryGroupings()).containsExactly("groceries");
        }

        @Test
        @DisplayName("when a request carries a message reference - then messageReference() reads back unchanged")
        void whenRequestCarriesAMessageReference_thenMessageReferenceReadsBackUnchanged() {
            IncomingMessageId incomingMessageId = newIncomingMessageId();

            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    catalogue("groceries"),
                    "groceries",
                    Optional.of(CurrencyCode.of("EUR")),
                    1L,
                    incomingMessageId,
                    CURRENT_DATE);

            assertThat(request.incomingMessageId()).isEqualTo(incomingMessageId);
        }

        @Test
        @DisplayName("when the message reference is null - then throws InvalidExtractionRequestException")
        void whenMessageReferenceIsNull_thenThrowsInvalidExtractionRequestException() {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            catalogue("groceries"),
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            1L,
                            null,
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @Test
        @DisplayName("when the current date is null - then throws InvalidExtractionRequestException")
        void whenCurrentDateIsNull_thenThrowsInvalidExtractionRequestException() {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            catalogue("groceries"),
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            1L,
                            newIncomingMessageId(),
                            null))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @Test
        @DisplayName(
                "when an otherwise valid request carries a current date - " + "then currentDate() reads back unchanged")
        void whenRequestIsOtherwiseValidWithACurrentDate_thenCurrentDateReadsBackUnchanged() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    catalogue("groceries"),
                    "groceries",
                    Optional.of(CurrencyCode.of("EUR")),
                    1L,
                    newIncomingMessageId(),
                    CURRENT_DATE);

            assertThat(request.currentDate()).isEqualTo(CURRENT_DATE);
        }
    }
}
