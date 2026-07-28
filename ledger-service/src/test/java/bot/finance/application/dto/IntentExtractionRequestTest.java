package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.CurrencyCode;
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
            IntentExtractionRequest request =
                    new IntentExtractionRequest("lunch 12 euro", List.of("food"), Optional.of(CurrencyCode.of("EUR")));

            assertThat(request.text()).isEqualTo("lunch 12 euro");
            assertThat(request.knownCategories()).containsExactly("food");
            assertThat(request.defaultCurrency()).contains(CurrencyCode.of("EUR"));
        }

        @ParameterizedTest(name = "text={0}")
        @MethodSource("nullOrBlankText")
        @DisplayName("when text is null or blank - then throws InvalidExtractionRequestException")
        void whenTextIsNullOrBlank_thenThrowsInvalidExtractionRequestException(String text) {
            assertThatThrownBy(() ->
                            new IntentExtractionRequest(text, List.of("food"), Optional.of(CurrencyCode.of("EUR"))))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrBlankText() {
            return Stream.of(arguments((Object) null), arguments("   "));
        }

        @ParameterizedTest
        @MethodSource("nullOrEmptyCategories")
        @DisplayName("when known categories is null or empty - then throws InvalidExtractionRequestException")
        void whenKnownCategoriesIsNullOrEmpty_thenThrowsInvalidExtractionRequestException(
                List<String> knownCategories) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro", knownCategories, Optional.of(CurrencyCode.of("EUR"))))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> nullOrEmptyCategories() {
            return Stream.of(arguments((Object) null), arguments(List.of()));
        }

        @ParameterizedTest
        @MethodSource("categoriesWithInvalidEntry")
        @DisplayName(
                "when known categories contains a null or blank entry - then throws InvalidExtractionRequestException")
        void whenKnownCategoriesContainsNullOrBlankEntry_thenThrowsInvalidExtractionRequestException(
                List<String> knownCategories) {
            assertThatThrownBy(() -> new IntentExtractionRequest(
                            "lunch 12 euro", knownCategories, Optional.of(CurrencyCode.of("EUR"))))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        static Stream<Arguments> categoriesWithInvalidEntry() {
            return Stream.of(arguments(Arrays.asList("food", null)), arguments(List.of("food", "   ")));
        }

        @Test
        @DisplayName("when the default currency optional is null - then throws InvalidExtractionRequestException")
        void whenDefaultCurrencyIsNull_thenThrowsInvalidExtractionRequestException() {
            assertThatThrownBy(() -> new IntentExtractionRequest("lunch 12 euro", List.of("food"), null))
                    .isInstanceOf(InvalidExtractionRequestException.class);
        }

        @Test
        @DisplayName("when the default currency is empty - then default currency comes back empty")
        void whenDefaultCurrencyIsEmpty_thenDefaultCurrencyComesBackEmpty() {
            IntentExtractionRequest request =
                    new IntentExtractionRequest("lunch 12 euro", List.of("food"), Optional.empty());

            assertThat(request.defaultCurrency()).isEmpty();
        }

        @Test
        @DisplayName("when the mutable category list handed to the constructor is modified afterwards - "
                + "then known categories is unchanged")
        void whenKnownCategoriesListIsModifiedAfterConstruction_thenKnownCategoriesIsUnchanged() {
            List<String> mutableCategories = new ArrayList<>(List.of("food"));

            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro", mutableCategories, Optional.of(CurrencyCode.of("EUR")));
            mutableCategories.add("transport");

            assertThat(request.knownCategories()).containsExactly("food");
        }
    }
}
