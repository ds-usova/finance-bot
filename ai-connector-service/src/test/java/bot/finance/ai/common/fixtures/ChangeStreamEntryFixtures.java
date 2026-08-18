package bot.finance.ai.common.fixtures;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builders of a change-stream entry body - {@code id}, {@code type}, {@code occurredAt} and {@code payload} -
 * in the shape the ledger's {@code ledger.cdc} stream carries, for
 * {@link bot.finance.ai.adapter.redis.ChangeStreamEntryReader} and
 * {@link bot.finance.ai.adapter.redis.ChangeStreamConsumer} to read.
 */
public final class ChangeStreamEntryFixtures {

    private static final String DEFAULT_OCCURRED_AT = "2026-08-18T12:00:00Z";

    private ChangeStreamEntryFixtures() {}

    public static Map<String, String> proposalCreated(
            long eventId,
            long userId,
            String incomingMessageId,
            long expenseId,
            String description,
            String merchant,
            String amount,
            String currencyCode,
            long categoryId,
            String categoryName,
            Long groupingId,
            String groupingName) {
        return entry(
                eventId,
                "ProposalCreated",
                payload(
                        userId,
                        incomingMessageId,
                        expenseId,
                        "PENDING",
                        description,
                        merchant,
                        amount,
                        currencyCode,
                        categoryId,
                        categoryName,
                        groupingId,
                        groupingName));
    }

    public static Map<String, String> proposalRefiled(
            long eventId,
            long userId,
            String incomingMessageId,
            long expenseId,
            String description,
            String merchant,
            String amount,
            String currencyCode,
            long categoryId,
            String categoryName,
            Long groupingId,
            String groupingName) {
        return entry(
                eventId,
                "ProposalRefiled",
                payload(
                        userId,
                        incomingMessageId,
                        expenseId,
                        "PENDING",
                        description,
                        merchant,
                        amount,
                        currencyCode,
                        categoryId,
                        categoryName,
                        groupingId,
                        groupingName));
    }

    public static Map<String, String> proposalDiscarded(
            long eventId,
            long userId,
            String incomingMessageId,
            long expenseId,
            String description,
            String merchant,
            String amount,
            String currencyCode,
            long categoryId,
            String categoryName,
            Long groupingId,
            String groupingName) {
        return entry(
                eventId,
                "ProposalDiscarded",
                payload(
                        userId,
                        incomingMessageId,
                        expenseId,
                        "PENDING",
                        description,
                        merchant,
                        amount,
                        currencyCode,
                        categoryId,
                        categoryName,
                        groupingId,
                        groupingName));
    }

    public static Map<String, String> proposalAccepted(
            long eventId,
            long userId,
            String incomingMessageId,
            long expenseId,
            String description,
            String merchant,
            String amount,
            String currencyCode,
            long categoryId,
            String categoryName,
            Long groupingId,
            String groupingName) {
        return entry(
                eventId,
                "ProposalAccepted",
                payload(
                        userId,
                        incomingMessageId,
                        expenseId,
                        "RECORDED",
                        description,
                        merchant,
                        amount,
                        currencyCode,
                        categoryId,
                        categoryName,
                        groupingId,
                        groupingName));
    }

    public static Map<String, String> expenseRecorded(
            long eventId,
            long userId,
            String incomingMessageId,
            long expenseId,
            String description,
            String merchant,
            String amount,
            String currencyCode,
            long categoryId,
            String categoryName,
            Long groupingId,
            String groupingName) {
        return entry(
                eventId,
                "ExpenseRecorded",
                payload(
                        userId,
                        incomingMessageId,
                        expenseId,
                        "RECORDED",
                        description,
                        merchant,
                        amount,
                        currencyCode,
                        categoryId,
                        categoryName,
                        groupingId,
                        groupingName));
    }

    public static Map<String, String> expenseRefiled(
            long eventId,
            long userId,
            String incomingMessageId,
            long expenseId,
            String description,
            String merchant,
            String amount,
            String currencyCode,
            long categoryId,
            String categoryName,
            Long groupingId,
            String groupingName) {
        return entry(
                eventId,
                "ExpenseRefiled",
                payload(
                        userId,
                        incomingMessageId,
                        expenseId,
                        "RECORDED",
                        description,
                        merchant,
                        amount,
                        currencyCode,
                        categoryId,
                        categoryName,
                        groupingId,
                        groupingName));
    }

    /** A body naming a {@code type} outside the six spending types this reader knows. */
    public static Map<String, String> unknownType(long eventId, long userId, long expenseId) {
        return entry(
                eventId,
                "CategoryRenamed",
                payload(userId, null, expenseId, "PENDING", "n/a", null, "1.00", "USD", 1L, "Cat", null, null));
    }

    /** A body with no {@code payload} field but still publishable - Redis XADD refuses an entry with none. */
    public static Map<String, String> withNoPayload() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("id", "1");
        body.put("type", "ProposalCreated");
        body.put("occurredAt", DEFAULT_OCCURRED_AT);
        return body;
    }

    /** A body whose {@code payload} field is not valid JSON. */
    public static Map<String, String> withNonJsonPayload() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("id", "1");
        body.put("type", "ProposalCreated");
        body.put("occurredAt", DEFAULT_OCCURRED_AT);
        body.put("payload", "not-json");
        return body;
    }

    private static Map<String, String> entry(long eventId, String type, String payload) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("id", String.valueOf(eventId));
        body.put("type", type);
        body.put("occurredAt", DEFAULT_OCCURRED_AT);
        body.put("payload", payload);
        return body;
    }

    private static String payload(
            long userId,
            String incomingMessageId,
            long expenseId,
            String status,
            String description,
            String merchant,
            String amount,
            String currencyCode,
            long categoryId,
            String categoryName,
            Long groupingId,
            String groupingName) {
        return """
                {"userId":%d,"incomingMessageId":%s,"expenseId":%d,"status":"%s","description":"%s",\
                "merchant":%s,"amount":"%s","currencyCode":"%s","category":{"id":%d,"name":"%s"},\
                "grouping":%s}"""
                .formatted(
                        userId,
                        incomingMessageId == null ? "null" : "\"" + incomingMessageId + "\"",
                        expenseId,
                        status,
                        description,
                        merchant == null ? "null" : "\"" + merchant + "\"",
                        amount,
                        currencyCode,
                        categoryId,
                        categoryName,
                        grouping(groupingId, groupingName));
    }

    private static String grouping(Long groupingId, String groupingName) {
        if (groupingId == null) {
            return "null";
        }
        return """
                {"id":%d,"name":"%s"}""".formatted(groupingId, groupingName);
    }
}
