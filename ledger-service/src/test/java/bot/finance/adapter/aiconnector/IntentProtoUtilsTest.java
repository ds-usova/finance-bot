package bot.finance.adapter.aiconnector;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.dto.KnownCategory;
import bot.finance.domain.value.CurrencyCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IntentProtoUtilsTest {

    @Nested
    @DisplayName("mapping an intent-extraction request to the generated proto request")
    class ToProtoRequest {

        @Test
        @DisplayName("when the request carries text, three categories, and default currency EUR - then the "
                + "generated request carries the text, the categories in order, and default_currency EUR")
        void whenRequestCarriesTextThreeCategoriesAndDefaultCurrencyEur_thenGeneratedRequestCarriesThemAll() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of(
                            new KnownCategory("Groceries", "Food"),
                            new KnownCategory("Transport", "Travel"),
                            new KnownCategory("Other", "Other")),
                    Optional.of(CurrencyCode.of("EUR")),
                    "user-external-id");

            ExtractIntentsRequest protoRequest = IntentProtoUtils.toProtoRequest(request);

            assertThat(protoRequest.getText()).isEqualTo("lunch 12 euro");
            assertThat(protoRequest.getKnownCategoriesList()).hasSize(3);
            assertThat(protoRequest.getKnownCategories(0).getName()).isEqualTo("Groceries");
            assertThat(protoRequest.getKnownCategories(0).getParentName()).isEqualTo("Food");
            assertThat(protoRequest.hasDefaultCurrency()).isTrue();
            assertThat(protoRequest.getDefaultCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when the request's default currency is empty - then the generated request reports "
                + "hasDefaultCurrency() as false")
        void whenRequestDefaultCurrencyIsEmpty_thenGeneratedRequestReportsHasDefaultCurrencyAsFalse() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of(new KnownCategory("Groceries", "Food"), new KnownCategory("Other", "Other")),
                    Optional.empty(),
                    "user-external-id");

            ExtractIntentsRequest protoRequest = IntentProtoUtils.toProtoRequest(request);

            assertThat(protoRequest.hasDefaultCurrency()).isFalse();
        }

        @Test
        @DisplayName("when the request carries two known categories - then the generated request holds two "
                + "KnownCategory messages, each with its name and parent name, in order")
        void whenRequestCarriesTwoKnownCategories_thenGeneratedRequestHoldsTwoKnownCategoryMessagesInOrder() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of(new KnownCategory("Groceries", "Food"), new KnownCategory("Transport", "Travel")),
                    Optional.of(CurrencyCode.of("EUR")),
                    "user-external-id");

            ExtractIntentsRequest protoRequest = IntentProtoUtils.toProtoRequest(request);

            assertThat(protoRequest.getKnownCategoriesList()).hasSize(2);
            assertThat(protoRequest.getKnownCategories(0).getName()).isEqualTo("Groceries");
            assertThat(protoRequest.getKnownCategories(0).getParentName()).isEqualTo("Food");
            assertThat(protoRequest.getKnownCategories(1).getName()).isEqualTo("Transport");
            assertThat(protoRequest.getKnownCategories(1).getParentName()).isEqualTo("Travel");
        }
    }
}
