package bot.finance.adapter.persistence;

import static bot.finance.common.fixtures.JsonUtils.readJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpendingEventRendererTest {

    private static final String EVENT_TYPE = "ProposalCreated";

    private final SpendingEventRenderer renderer = new SpendingEventRenderer();

    private static SpendingRowProjection rowWith(
            long amountMinorUnits, String currencyCode, String merchant, String incomingMessageId) {
        return new SpendingRowProjection(
                42L,
                7L,
                incomingMessageId,
                "PENDING",
                "Milk",
                merchant,
                amountMinorUnits,
                currencyCode,
                Instant.parse("2026-01-01T10:00:00Z"),
                20L,
                "Food",
                30L,
                "Household");
    }

    private static SpendingRowProjection fullRow() {
        return rowWith(1500L, "EUR", "Corner Shop", "incoming-msg-1");
    }

    @Nested
    @DisplayName("render()")
    class Render {

        @Test
        @DisplayName(
                "when a row has a merchant, a category and a grouping - then the payload carries the row's " + "values")
        void whenRowHasMerchantCategoryAndGrouping_thenPayloadCarriesTheRowsValues() {
            SpendingRowProjection row = fullRow();
            Instant occurredAt = Instant.parse("2026-02-01T12:30:00Z");

            LedgerEvent event = renderer.render(EVENT_TYPE, row, occurredAt);

            assertThat(event.id()).isNotNull();
            assertThat(event.type()).isEqualTo(EVENT_TYPE);
            assertThat(event.occurredAt()).isEqualTo(occurredAt);
            assertThat(event.payload()).isNotNull();

            JsonNode payload = readJson(event.payload());
            assertThat(payload.get("userId").asLong()).isEqualTo(row.userId());
            assertThat(payload.get("incomingMessageId").asText()).isEqualTo(row.incomingMessageId());
            assertThat(payload.get("expenseId").asLong()).isEqualTo(row.id());
            assertThat(payload.get("status").asText()).isEqualTo(row.status());
            assertThat(payload.get("description").asText()).isEqualTo(row.description());
            assertThat(payload.get("merchant").asText()).isEqualTo(row.merchant());
            assertThat(payload.get("currencyCode").asText()).isEqualTo(row.currencyCode());
            assertThat(payload.get("category").get("id").asLong()).isEqualTo(row.categoryId());
            assertThat(payload.get("category").get("name").asText()).isEqualTo(row.categoryName());
            assertThat(payload.get("grouping").get("id").asLong()).isEqualTo(row.groupingId());
            assertThat(payload.get("grouping").get("name").asText()).isEqualTo(row.groupingName());
        }

        @Test
        @DisplayName("when the amount is 350 minor units in EUR - then the payload's amount is \"3.50\"")
        void whenAmountIs350MinorUnitsInEur_thenPayloadAmountIsThreeFifty() {
            SpendingRowProjection row = rowWith(350L, "EUR", "Corner Shop", "incoming-msg-1");

            LedgerEvent event = renderer.render(EVENT_TYPE, row, Instant.now());

            assertThat(event.payload()).isNotNull();
            JsonNode payload = readJson(event.payload());
            assertThat(payload.get("amount").asText()).isEqualTo("3.50");
        }

        @Test
        @DisplayName("when the amount is 7200 minor units in a currency with no minor unit - then the payload's "
                + "amount is \"7200\"")
        void whenAmountIs7200MinorUnitsInCurrencyWithNoMinorUnit_thenPayloadAmountIs7200() {
            SpendingRowProjection row = rowWith(7200L, "JPY", "Corner Shop", "incoming-msg-1");

            LedgerEvent event = renderer.render(EVENT_TYPE, row, Instant.now());

            assertThat(event.payload()).isNotNull();
            JsonNode payload = readJson(event.payload());
            assertThat(payload.get("amount").asText()).isEqualTo("7200");
        }

        @Test
        @DisplayName("when the row's grouping id and name are both null - then the payload's grouping is JSON null")
        void whenGroupingIdAndNameAreBothNull_thenPayloadGroupingIsJsonNull() {
            SpendingRowProjection row = new SpendingRowProjection(
                    42L,
                    7L,
                    "incoming-msg-1",
                    "PENDING",
                    "Milk",
                    "Corner Shop",
                    1500L,
                    "EUR",
                    Instant.parse("2026-01-01T10:00:00Z"),
                    20L,
                    "Food",
                    null,
                    null);

            LedgerEvent event = renderer.render(EVENT_TYPE, row, Instant.now());

            assertThat(event.payload()).isNotNull();
            JsonNode payload = readJson(event.payload());
            assertThat(payload.get("grouping").isNull()).isTrue();
            assertThat(payload.get("description").asText()).isEqualTo(row.description());
            assertThat(payload.get("category").get("id").asLong()).isEqualTo(row.categoryId());
        }

        @Test
        @DisplayName("when the row's merchant and incoming message id are both null - then both are JSON null")
        void whenMerchantAndIncomingMessageIdAreNull_thenBothAreJsonNull() {
            SpendingRowProjection row = rowWith(1500L, "EUR", null, null);

            LedgerEvent event = renderer.render(EVENT_TYPE, row, Instant.now());

            assertThat(event.payload()).isNotNull();
            JsonNode payload = readJson(event.payload());
            assertThat(payload.get("merchant").isNull()).isTrue();
            assertThat(payload.get("incomingMessageId").isNull()).isTrue();
        }

        @Test
        @DisplayName("when the same row is rendered twice - then the two payloads are equal and the two ids differ")
        void whenSameRowIsRenderedTwice_thenPayloadsAreEqualAndIdsDiffer() {
            SpendingRowProjection row = fullRow();
            Instant occurredAt = Instant.parse("2026-02-01T12:30:00Z");

            LedgerEvent first = renderer.render(EVENT_TYPE, row, occurredAt);
            LedgerEvent second = renderer.render(EVENT_TYPE, row, occurredAt);

            assertThat(first.payload()).isNotNull();
            assertThat(first.payload()).isEqualTo(second.payload());
            assertThat(first.id()).isNotEqualTo(second.id());
        }
    }
}
