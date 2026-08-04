package bot.finance.adapter.aiconnector;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IntentProtoUtilsTest {

    @Nested
    @DisplayName("mapping an intent-extraction request to the generated proto request")
    class ToProtoRequest {

        // TODO RU06: replace with the grouping-ordering and catch-all assertions, and the known_categories-is-
        // reserved descriptor assertion. See plan.md RU06 for the scenarios and update: bullets.

        @Test
        @DisplayName("when the request carries text, three groupings, and default currency EUR - then the "
                + "generated request carries the text, the groupings in order, and default_currency EUR")
        void whenRequestCarriesTextThreeCategoriesAndDefaultCurrencyEur_thenGeneratedRequestCarriesThemAll() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of("Groceries", "Transport", "Other"),
                    "Other",
                    Optional.of(CurrencyCode.of("EUR")),
                    "user-external-id",
                    MessageReference.newReference());

            ExtractIntentsRequest protoRequest = IntentProtoUtils.toProtoRequest(request);

            assertThat(protoRequest.getText()).isEqualTo("lunch 12 euro");
            assertThat(protoRequest.getCategoryGroupingsList()).containsExactly("Groceries", "Transport", "Other");
            assertThat(protoRequest.getCatchAllGrouping()).isEqualTo("Other");
            assertThat(protoRequest.hasDefaultCurrency()).isTrue();
            assertThat(protoRequest.getDefaultCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when the request's default currency is empty - then the generated request reports "
                + "hasDefaultCurrency() as false")
        void whenRequestDefaultCurrencyIsEmpty_thenGeneratedRequestReportsHasDefaultCurrencyAsFalse() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of("Groceries", "Other"),
                    "Other",
                    Optional.empty(),
                    "user-external-id",
                    MessageReference.newReference());

            ExtractIntentsRequest protoRequest = IntentProtoUtils.toProtoRequest(request);

            assertThat(protoRequest.hasDefaultCurrency()).isFalse();
        }
    }
}
