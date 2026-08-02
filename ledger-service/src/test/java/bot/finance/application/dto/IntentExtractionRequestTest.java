package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
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

    @Nested
    @DisplayName("constructing an intent extraction request")
    class IntentExtractionRequestConstructor {

        @Test
        @DisplayName("when text, one category, and a default currency are valid - then it holds all three")
        void whenTextOneCategoryAndDefaultCurrencyAreValid_thenItHoldsAllThree() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of(new KnownCategory("food", "groceries")),
                    Optional.of(CurrencyCode.of("EUR")),
                    "user-external-id",
                    MessageReference.newReference());

            assertThat(request.text()).isEqualTo("lunch 12 euro");
            assertThat(request.knownCategories()).containsExactly(new KnownCategory("food", "groceries"));
            assertThat(request.defaultCurrency()).contains(CurrencyCode.of("EUR"));
        }

        @Test
        @DisplayName("when text, one category, an empty currency and a non-blank external id are valid - "
                + "then every component reads back unchanged and known categories is unmodifiable")
        void whenTextOneCategoryEmptyCurrencyAndUserExternalIdAreValid_thenEveryComponentReadsBackUnchangedAndKnownCategoriesIsUnmodifiable() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of(new KnownCategory("food", "groceries")),
                    Optional.empty(),
                    "user-external-id",
                    MessageReference.newReference());

            assertThat(request.text()).isEqualTo("lunch 12 euro");
            assertThat(request.knownCategories()).containsExactly(new KnownCategory("food", "groceries"));
            assertThat(request.defaultCurrency()).isEmpty();
            assertThat(request.userExternalId()).isEqualTo("user-external-id");
            assertThatThrownBy(() -> request.knownCategories().add(new KnownCategory("transport", "travel")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @ParameterizedTest(name = "text={0}")
        @MethodSource("nullOrBlankText")
        @DisplayName("when text is null or blank - then throws InvalidExtractionRequestException")
        void whenTextIsNullOrBlank_thenThrowsInvalidExtractionRequestException(String text) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            text,
                            List.of(new KnownCategory("food", "groceries")),
                            Optional.of(CurrencyCode.of("EUR")),
                            "user-external-id",
                            MessageReference.newReference()))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrBlankText() {
            return Stream.of(arguments((Object) null), arguments("   "));
        }

        @ParameterizedTest
        @MethodSource("nullOrEmptyCategories")
        @DisplayName("when known categories is null or empty - then throws InvalidExtractionRequestException")
        void whenKnownCategoriesIsNullOrEmpty_thenThrowsInvalidExtractionRequestException(
                List<KnownCategory> knownCategories) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            knownCategories,
                            Optional.of(CurrencyCode.of("EUR")),
                            "user-external-id",
                            MessageReference.newReference()))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrEmptyCategories() {
            return Stream.of(arguments((Object) null), arguments(List.of()));
        }

        @Test
        @DisplayName(
                "when known categories contains a null entry - then throws InvalidExtractionRequestException")
        void whenKnownCategoriesContainsNullEntry_thenThrowsInvalidExtractionRequestException() {
            List<KnownCategory> knownCategories = Arrays.asList(new KnownCategory("food", "groceries"), null);

            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            knownCategories,
                            Optional.of(CurrencyCode.of("EUR")),
                            "user-external-id",
                            MessageReference.newReference()))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @Test
        @DisplayName("when the default currency optional is null - then throws InvalidExtractionRequestException")
        void whenDefaultCurrencyIsNull_thenThrowsInvalidExtractionRequestException() {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro",
                            List.of(new KnownCategory("food", "groceries")),
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
                    List.of(new KnownCategory("food", "groceries")),
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
                            List.of(new KnownCategory("food", "groceries")),
                            Optional.of(CurrencyCode.of("EUR")),
                            userExternalId,
                            MessageReference.newReference()))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrBlankUserExternalId() {
            return Stream.of(arguments((Object) null), arguments("   "));
        }

        @Test
        @DisplayName("when the mutable category list handed to the constructor is modified afterwards - "
                + "then known categories is unchanged")
        void whenKnownCategoriesListIsModifiedAfterConstruction_thenKnownCategoriesIsUnchanged() {
            List<KnownCategory> mutableCategories = new ArrayList<>(List.of(new KnownCategory("food", "groceries")));

            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    mutableCategories,
                    Optional.of(CurrencyCode.of("EUR")),
                    "user-external-id",
                    MessageReference.newReference());
            mutableCategories.add(new KnownCategory("transport", "travel"));

            assertThat(request.knownCategories()).containsExactly(new KnownCategory("food", "groceries"));
        }

        @Test
        @DisplayName("when text, categories, currency, external id and a message reference are valid - "
                + "then messageReference reads back unchanged")
        void whenTextCategoriesCurrencyExternalIdAndMessageReferenceAreValid_thenMessageReferenceReadsBackUnchanged() {
            MessageReference messageReference = MessageReference.newReference();

            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of(new KnownCategory("food", "groceries")),
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
                            List.of(new KnownCategory("food", "groceries")),
                            Optional.of(CurrencyCode.of("EUR")),
                            "user-external-id",
                            null))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }
    }
}
