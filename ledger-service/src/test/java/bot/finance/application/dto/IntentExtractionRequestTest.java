package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import java.util.ArrayList;
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
                    MessageReference.newReference());

            assertThat(request.text()).isEqualTo("lunch 12 euro");
            assertThat(request.categoryGroupings()).containsExactly("groceries");
            assertThat(request.defaultCurrency()).contains(CurrencyCode.of("EUR"));
        }

        // TODO RU02: assert the unmodifiability of the categoryGroupings component and read back the
        // catchAllGrouping one.
        @Test
        @DisplayName("when text, one grouping, an empty currency, a catch-all and a non-blank external id are "
                + "valid - then every component reads back unchanged and category groupings is unmodifiable")
        void
                whenTextOneCategoryEmptyCurrencyAndUserExternalIdAreValid_thenEveryComponentReadsBackUnchangedAndKnownCategoriesIsUnmodifiable() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of("groceries"),
                    "groceries",
                    Optional.empty(),
                    "user-external-id",
                    MessageReference.newReference());

            assertThat(request.text()).isEqualTo("lunch 12 euro");
            assertThat(request.categoryGroupings()).containsExactly("groceries");
            assertThat(request.catchAllGrouping()).isEqualTo("groceries");
            assertThat(request.defaultCurrency()).isEmpty();
            assertThat(request.userExternalId()).isEqualTo("user-external-id");
            assertThatThrownBy(() -> request.categoryGroupings().add("transport"))
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
                            MessageReference.newReference()))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrBlankText() {
            return Stream.of(arguments((Object) null), arguments("   "));
        }

        // TODO RU02: replace with the grouping-list scenarios (null, empty, null element, blank element) and
        // the catch-all scenarios (null, blank, not one of the groupings). See plan.md RU02.

        @Test
        @DisplayName("when the default currency optional is null - then throws InvalidExtractionRequestException")
        void whenDefaultCurrencyIsNull_thenThrowsInvalidExtractionRequestException() {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            List.of("groceries"),
                            "groceries",
                            null,
                            "user-external-id",
                            MessageReference.newReference()))
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
                    MessageReference.newReference());

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
                            MessageReference.newReference()))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrBlankUserExternalId() {
            return Stream.of(arguments((Object) null), arguments("   "));
        }

        // TODO RU02: assert the copy on the categoryGroupings component.
        @Test
        @DisplayName("when the mutable grouping list handed to the constructor is modified afterwards - "
                + "then category groupings is unchanged")
        void whenKnownCategoriesListIsModifiedAfterConstruction_thenKnownCategoriesIsUnchanged() {
            List<String> mutableGroupings = new ArrayList<>(List.of("groceries"));

            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    mutableGroupings,
                    "groceries",
                    Optional.of(CurrencyCode.of("EUR")),
                    "user-external-id",
                    MessageReference.newReference());
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
                    messageReference);

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
                            null))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }
    }
}
