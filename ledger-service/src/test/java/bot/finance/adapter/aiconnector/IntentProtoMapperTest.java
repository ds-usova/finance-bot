package bot.finance.adapter.aiconnector;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IntentProtoMapperTest {

    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 5);

    @Nested
    @DisplayName("mapping an intent-extraction request to the generated proto request")
    class ToProtoRequest {

        @Test
        @DisplayName("when the request carries text, three groupings and default currency EUR - then the generated "
                + "request carries them all")
        void whenRequestCarriesTextThreeCategoriesAndDefaultCurrencyEur_thenGeneratedRequestCarriesThemAll() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of("Groceries", "Transport", "Other"),
                    "Other",
                    Optional.of(CurrencyCode.of("EUR")),
                    "user-external-id",
                    IncomingMessageId.of(java.util.UUID.randomUUID().toString()),
                    CURRENT_DATE);

            ExtractIntentsRequest protoRequest = IntentProtoMapper.toProtoRequest(request);

            assertThat(protoRequest.getText()).isEqualTo("lunch 12 euro");
            assertThat(protoRequest.getCategoryGroupingsList()).containsExactly("Groceries", "Transport", "Other");
            assertThat(protoRequest.getCatchAllGrouping()).isEqualTo("Other");
            assertThat(protoRequest.hasDefaultCurrency()).isTrue();
            assertThat(protoRequest.getDefaultCurrency()).isEqualTo("EUR");
            assertThat(protoRequest.getCurrentDate()).isEqualTo("2026-08-05");
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
                    IncomingMessageId.of(java.util.UUID.randomUUID().toString()),
                    CURRENT_DATE);

            ExtractIntentsRequest protoRequest = IntentProtoMapper.toProtoRequest(request);

            assertThat(protoRequest.hasDefaultCurrency()).isFalse();
        }

        @Test
        @DisplayName("when the request carries a current date - then the generated request carries it as current_date")
        void whenRequestCarriesCurrentDate_thenGeneratedRequestCarriesItAsCurrentDate() {
            IntentExtractionRequest request = new IntentExtractionRequest(
                    "lunch 12 euro",
                    List.of("Groceries", "Other"),
                    "Other",
                    Optional.empty(),
                    "user-external-id",
                    IncomingMessageId.of(java.util.UUID.randomUUID().toString()),
                    CURRENT_DATE);

            ExtractIntentsRequest protoRequest = IntentProtoMapper.toProtoRequest(request);

            assertThat(protoRequest.getCurrentDate()).isEqualTo("2026-08-05");
        }
    }
}
