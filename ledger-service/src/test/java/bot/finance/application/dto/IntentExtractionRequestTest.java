package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
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

class IntentExtractionRequestTest {

    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 5);

    @Nested
    @DisplayName("constructing an intent extraction request")
    class IntentExtractionRequestConstructor {

        @Test
        @DisplayName("when text, one grouping, and a default currency are valid - then it holds all three")
        void whenTextOneCategoryAndDefaultCurrencyAreValid_thenItHoldsAllThree() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of("groceries"),
                    "groceries",
                    Optional.of(CurrencyCode.of("EUR")),
                    "user-external-id",
                    MessageReference.newReference(),
                    CURRENT_DATE);

            assertThat(request.text()).isEqualTo("lunch 12 euro");
            assertThat(request.categoryGroupings()).containsExactly("groceries");
            assertThat(request.defaultCurrency()).contains(CurrencyCode.of("EUR"));
        }

        @Test
        @DisplayName("when text, two groupings, a catch-all among them, an empty currency and a non-blank "
                + "external id are valid - then every component reads back unchanged and category groupings is "
                + "unmodifiable")
        void
                whenTextTwoGroupingsCatchAllEmptyCurrencyAndUserExternalIdAreValid_thenEveryComponentReadsBackUnchangedAndCategoryGroupingsIsUnmodifiable() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of("groceries", "transport"),
                    "transport",
                    Optional.empty(),
                    "user-external-id",
                    MessageReference.newReference(),
                    CURRENT_DATE);

            assertThat(request.text()).isEqualTo("lunch 12 euro");
            assertThat(request.categoryGroupings()).containsExactly("groceries", "transport");
            assertThat(request.catchAllGrouping()).isEqualTo("transport");
            assertThat(request.defaultCurrency()).isEmpty();
            assertThat(request.userExternalId()).isEqualTo("user-external-id");
            assertThatThrownBy(() -> request.categoryGroupings().add("utilities"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @ParameterizedTest(name = "text={0}")
        @MethodSource("nullOrBlankText")
        @DisplayName("when text is null or blank - then throws InvalidExtractionRequestException")
        void whenTextIsNullOrBlank_thenThrowsInvalidExtractionRequestException(String text) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            text,
                            List.of("groceries"),
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            "user-external-id",
                            MessageReference.newReference(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrBlankText() {
            return Stream.of(arguments((Object) null), arguments("   "));
        }

        @ParameterizedTest(name = "categoryGroupings={0}")
        @MethodSource("nullOrEmptyCategoryGroupings")
        @DisplayName("when category groupings is null or empty - then throws InvalidExtractionRequestException")
        void whenCategoryGroupingsIsNullOrEmpty_thenThrowsInvalidExtractionRequestException(
                List<String> categoryGroupings) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            categoryGroupings,
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            "user-external-id",
                            MessageReference.newReference(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrEmptyCategoryGroupings() {
            return Stream.of(arguments((Object) null), arguments(List.of()));
        }

        @ParameterizedTest(name = "categoryGroupings={0}")
        @MethodSource("categoryGroupingsWithNullOrBlankElement")
        @DisplayName("when category groupings carries a null or blank element - then throws "
                + "InvalidExtractionRequestException")
        void whenCategoryGroupingsContainsNullOrBlankElement_thenThrowsInvalidExtractionRequestException(
                List<String> categoryGroupings) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            categoryGroupings,
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            "user-external-id",
                            MessageReference.newReference(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> categoryGroupingsWithNullOrBlankElement() {
            return Stream.of(arguments((Object) Arrays.asList("groceries", null)), arguments((Object)
                    List.of("groceries", "   ")));
        }

        @ParameterizedTest(name = "catchAllGrouping={0}")
        @MethodSource("nullOrBlankCatchAllGrouping")
        @DisplayName("when the catch-all grouping is null or blank - then throws InvalidExtractionRequestException")
        void whenCatchAllGroupingIsNullOrBlank_thenThrowsInvalidExtractionRequestException(String catchAllGrouping) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            List.of("groceries"),
                            catchAllGrouping,
                            Optional.of(CurrencyCode.of("EUR")),
                            "user-external-id",
                            MessageReference.newReference(),
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
                            List.of("groceries", "transport"),
                            "utilities",
                            Optional.of(CurrencyCode.of("EUR")),
                            "user-external-id",
                            MessageReference.newReference(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @Test
        @DisplayName("when the default currency optional is null - then throws InvalidExtractionRequestException")
        void whenDefaultCurrencyIsNull_thenThrowsInvalidExtractionRequestException() {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            List.of("groceries"),
                            "groceries",
                            null,
                            "user-external-id",
                            MessageReference.newReference(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @Test
        @DisplayName("when the default currency is empty - then default currency comes back empty")
        void whenDefaultCurrencyIsEmpty_thenDefaultCurrencyComesBackEmpty() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of("groceries"),
                    "groceries",
                    Optional.empty(),
                    "user-external-id",
                    MessageReference.newReference(),
                    CURRENT_DATE);

            assertThat(request.defaultCurrency()).isEmpty();
        }

        @ParameterizedTest(name = "userExternalId={0}")
        @MethodSource("nullOrBlankUserExternalId")
        @DisplayName("when userExternalId is null or blank - then throws InvalidExtractionRequestException")
        void whenUserExternalIdIsNullOrBlank_thenThrowsInvalidExtractionRequestException(String userExternalId) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            List.of("groceries"),
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            userExternalId,
                            MessageReference.newReference(),
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrBlankUserExternalId() {
            return Stream.of(arguments((Object) null), arguments("   "));
        }

        @Test
        @DisplayName("when the mutable grouping list handed to the constructor is modified afterwards - "
                + "then category groupings is unchanged")
        void whenGroupingListIsModifiedAfterConstruction_thenCategoryGroupingsIsUnchanged() {
            List<String> mutableGroupings = new ArrayList<>(List.of("groceries"));

            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    mutableGroupings,
                    "groceries",
                    Optional.of(CurrencyCode.of("EUR")),
                    "user-external-id",
                    MessageReference.newReference(),
                    CURRENT_DATE);
            mutableGroupings.add("transport");

            assertThat(request.categoryGroupings()).containsExactly("groceries");
        }

        @Test
        @DisplayName("when text, groupings, currency, external id and a message reference are valid - "
                + "then messageReference reads back unchanged")
        void whenTextCategoriesCurrencyExternalIdAndMessageReferenceAreValid_thenMessageReferenceReadsBackUnchanged() {
            MessageReference messageReference = MessageReference.newReference();

            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of("groceries"),
                    "groceries",
                    Optional.of(CurrencyCode.of("EUR")),
                    "user-external-id",
                    messageReference,
                    CURRENT_DATE);

            assertThat(request.messageReference()).isEqualTo(messageReference);
        }

        @Test
        @DisplayName("when the message reference is null - then throws InvalidExtractionRequestException")
        void whenMessageReferenceIsNull_thenThrowsInvalidExtractionRequestException() {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            List.of("groceries"),
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            "user-external-id",
                            null,
                            CURRENT_DATE))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @Test
        @DisplayName("when the current date is null - then throws InvalidExtractionRequestException")
        void whenCurrentDateIsNull_thenThrowsInvalidExtractionRequestException() {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            List.of("groceries"),
                            "groceries",
                            Optional.of(CurrencyCode.of("EUR")),
                            "user-external-id",
                            MessageReference.newReference(),
                            null))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @Test
        @DisplayName(
                "when an otherwise valid request carries a current date - " + "then currentDate() reads back unchanged")
        void whenRequestIsOtherwiseValidWithACurrentDate_thenCurrentDateReadsBackUnchanged() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of("groceries"),
                    "groceries",
                    Optional.of(CurrencyCode.of("EUR")),
                    "user-external-id",
                    MessageReference.newReference(),
                    CURRENT_DATE);

            assertThat(request.currentDate()).isEqualTo(CURRENT_DATE);
        }
    }
}
