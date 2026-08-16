package bot.finance.ai.adapter.redis;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CategoryRow;
import bot.finance.ai.domain.value.CategoryRowChange;
import bot.finance.ai.domain.value.ChangeOperation;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.RecordedChange;
import bot.finance.ai.domain.value.SpendingKind;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.SpendingRowChange;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ChangeStreamEntryReader {

    private static final String TABLE_PROPOSAL = "expense_proposal";
    private static final String TABLE_EXPENSE = "expense";
    private static final String TABLE_CATEGORY = "category";

    // Boot 4's Jackson autoconfiguration wires a JsonMapper bean, not a jackson-databind ObjectMapper, so this
    // reader owns its own the way the ledger's ChangeEventPublisher owns its own.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<LearnMessageOutcomeCommand> read(String entryId, Map<String, String> body) {
        JsonNode payload = parseJson(payloadOf(body));
        String table = payload.path("source").path("table").asText();
        String op = payload.path("op").asText();
        if (!isWatchedTable(table) || !isWatchedOp(op)) {
            return Optional.empty();
        }

        ChangeOperation changeOperation = toChangeOperation(op);
        JsonNode enrichment = enrichmentOf(body);
        RecordedChange change = TABLE_CATEGORY.equals(table)
                ? categoryRowChange(changeOperation, payload)
                : spendingRowChange(table, changeOperation, payload, enrichment);

        return Optional.of(new LearnMessageOutcomeCommand(entryId, change));
    }

    private static String payloadOf(Map<String, String> body) {
        String payload = body.get("payload");
        if (payload == null) {
            throw new InvalidValueException("Change-stream entry carries no payload");
        }
        return payload;
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new InvalidValueException("Change-stream entry field is not valid JSON");
        }
    }

    private static boolean isWatchedTable(String table) {
        return TABLE_PROPOSAL.equals(table) || TABLE_EXPENSE.equals(table) || TABLE_CATEGORY.equals(table);
    }

    private static boolean isWatchedOp(String op) {
        return "c".equals(op) || "u".equals(op) || "d".equals(op);
    }

    private static ChangeOperation toChangeOperation(String op) {
        return switch (op) {
            case "c" -> ChangeOperation.CREATED;
            case "u" -> ChangeOperation.UPDATED;
            default -> ChangeOperation.DELETED;
        };
    }

    private JsonNode enrichmentOf(Map<String, String> body) {
        String enrichment = body.get("enrichment");
        return enrichment == null ? null : parseJson(enrichment);
    }

    private static CategoryRowChange categoryRowChange(ChangeOperation op, JsonNode payload) {
        return new CategoryRowChange(op, categoryRow(payload.get("before")), categoryRow(payload.get("after")));
    }

    private static SpendingRowChange spendingRowChange(
            String table, ChangeOperation op, JsonNode payload, JsonNode enrichment) {
        SpendingKind kind = TABLE_PROPOSAL.equals(table) ? SpendingKind.PROPOSAL : SpendingKind.EXPENSE;
        String transactionId = payload.path("source").path("txId").asText();

        return new SpendingRowChange(
                kind,
                op,
                transactionId,
                spendingRow(payload.get("before"), sideOf(enrichment, "before")),
                spendingRow(payload.get("after"), sideOf(enrichment, "after")));
    }

    private static JsonNode sideOf(JsonNode enrichment, String side) {
        return enrichment == null ? null : enrichment.get(side);
    }

    private static Optional<CategoryRow> categoryRow(JsonNode row) {
        if (isAbsent(row)) {
            return Optional.empty();
        }

        return Optional.of(new CategoryRow(
                row.path("id").asLong(),
                row.path("user_id").asLong(),
                longOrEmpty(row, "parent_id"),
                textOf(row, "name")));
    }

    private static Optional<SpendingRow> spendingRow(JsonNode row, JsonNode enrichmentSide) {
        if (isAbsent(row)) {
            return Optional.empty();
        }

        return Optional.of(new SpendingRow(
                row.path("id").asLong(),
                row.path("user_id").asLong(),
                Optional.ofNullable(textOf(row, "incoming_message_id")),
                textOf(row, "description"),
                Optional.ofNullable(textOf(row, "merchant")),
                row.path("amount_minor_units").asLong(),
                CurrencyCode.of(textOf(row, "currency_code")),
                row.path("category_id").asLong(),
                Optional.ofNullable(textOf(enrichmentSide, "categoryName")),
                Optional.ofNullable(textOf(enrichmentSide, "groupingName"))));
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull();
    }

    private static String textOf(JsonNode node, String field) {
        if (isAbsent(node)) {
            return null;
        }
        JsonNode value = node.get(field);
        return isAbsent(value) ? null : value.asText();
    }

    private static Optional<Long> longOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return isAbsent(value) ? Optional.empty() : Optional.of(value.asLong());
    }
}
