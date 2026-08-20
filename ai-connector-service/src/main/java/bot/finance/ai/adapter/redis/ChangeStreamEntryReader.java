package bot.finance.ai.adapter.redis;

import bot.finance.ai.application.dto.LearnMessageOutcomeCommand;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CategoryRef;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.RecordedStatus;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.StreamPosition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ChangeStreamEntryReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Optional<LearnMessageOutcomeCommand> read(String entryId, Map<String, String> body) {
        String type = body.get("type");
        if (type == null) {
            throw new InvalidValueException("Type must not be null");
        }
        Optional<RecordedStatus> status = statusFor(type);
        if (status.isEmpty()) {
            return Optional.empty();
        }

        String payload = body.get("payload");
        if (payload == null) {
            throw new InvalidValueException("Payload must not be null");
        }
        SpendingRow entry = toRow(parsePayload(payload));
        StreamPosition position = parsePosition(entryId);

        return Optional.of(new LearnMessageOutcomeCommand(entryId, position, status.get(), entry));
    }

    private Optional<RecordedStatus> statusFor(String type) {
        return switch (type) {
            case "ProposalCreated", "ProposalRefiled" -> Optional.of(RecordedStatus.PROPOSED);
            case "ProposalDiscarded" -> Optional.of(RecordedStatus.DISCARDED);
            case "ProposalAccepted", "ExpenseRecorded", "ExpenseRefiled" -> Optional.of(RecordedStatus.ACCEPTED);
            default -> Optional.empty();
        };
    }

    private JsonNode parsePayload(String payload) {
        try {
            return MAPPER.readTree(payload);
        } catch (Exception e) {
            throw new InvalidValueException("Payload must be valid JSON");
        }
    }

    private SpendingRow toRow(JsonNode payload) {
        long expenseId = requiredPositiveLong(payload, "expenseId");
        long userId = requiredPositiveLong(payload, "userId");

        return new SpendingRow(
                expenseId,
                userId,
                optionalText(payload, "incomingMessageId"),
                payload.path("description").asText(),
                optionalText(payload, "merchant"),
                payload.path("amount").asText(),
                CurrencyCode.of(payload.path("currencyCode").asText()),
                category(payload),
                grouping(payload));
    }

    private CategoryRef category(JsonNode payload) {
        JsonNode category = payload.path("category");
        return new CategoryRef(
                category.path("id").asLong(), category.path("name").asText());
    }

    private CategoryRef grouping(JsonNode payload) {
        JsonNode grouping = payload.get("grouping");
        if (grouping == null || grouping.isNull()) {
            throw new InvalidValueException("grouping must name the grouping the category sits in");
        }
        return new CategoryRef(
                grouping.path("id").asLong(), grouping.path("name").asText());
    }

    private long requiredPositiveLong(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull() || !value.isIntegralNumber()) {
            throw new InvalidValueException(field + " must be a positive number");
        }
        long parsed = value.asLong();
        if (parsed <= 0) {
            throw new InvalidValueException(field + " must be positive");
        }
        return parsed;
    }

    private Optional<String> optionalText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        return Optional.of(value.asText());
    }

    private StreamPosition parsePosition(String entryId) {
        int dashIndex = entryId.indexOf('-');
        if (dashIndex < 0) {
            throw new InvalidValueException("Entry id must be \"<ms>-<seq>\"");
        }
        try {
            long ms = Long.parseLong(entryId.substring(0, dashIndex));
            long seq = Long.parseLong(entryId.substring(dashIndex + 1));
            return new StreamPosition(ms, seq);
        } catch (NumberFormatException e) {
            throw new InvalidValueException("Entry id must be \"<ms>-<seq>\"");
        }
    }
}
