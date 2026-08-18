package bot.finance.adapter.persistence;

import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Turns a changed row into a typed event with its JSON body.
 */
@Component
public class SpendingEventRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public LedgerEvent render(String type, SpendingRowProjection row, Instant occurredAt) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("userId", row.userId());
        payload.put("incomingMessageId", row.incomingMessageId());
        payload.put("expenseId", row.id());
        payload.put("status", row.status());
        payload.put("description", row.description());
        payload.put("merchant", row.merchant());
        payload.put("currencyCode", row.currencyCode());
        payload.put(
                "amount",
                new Money(row.amountMinorUnits(), CurrencyCode.of(row.currencyCode()))
                        .amount()
                        .toPlainString());

        ObjectNode category = payload.putObject("category");
        category.put("id", row.categoryId());
        category.put("name", row.categoryName());

        if (row.groupingId() == null && row.groupingName() == null) {
            payload.putNull("grouping");
        } else {
            ObjectNode grouping = payload.putObject("grouping");
            grouping.put("id", row.groupingId());
            grouping.put("name", row.groupingName());
        }

        return new LedgerEvent(UUID.randomUUID(), type, occurredAt, payload.toString());
    }
}
