package bot.finance.ai.common.fixtures;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builders of a change-stream entry body — {@code payload} and {@code enrichment} — in the shape the ledger's
 * change stream carries, for {@link bot.finance.ai.adapter.redis.ChangeStreamEntryReader} and
 * {@link bot.finance.ai.adapter.redis.ChangeStreamConsumer} to read.
 */
public final class ChangeStreamEntryFixtures {

    private static final long DEFAULT_LSN = 100L;
    private static final long DEFAULT_TS_MS = 1_700_000_000_000L;

    private ChangeStreamEntryFixtures() {}

    public static Map<String, String> proposalCreated(
            long proposalId,
            long userId,
            String incomingMessageId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            long categoryId,
            String categoryName,
            String groupingName,
            String txId) {
        String after = spendingRowJson(
                proposalId,
                userId,
                incomingMessageId,
                description,
                merchant,
                amountMinorUnits,
                currencyCode,
                categoryId);
        return entry("expense_proposal", "c", null, after, txId, null, enrichmentSide(categoryName, groupingName));
    }

    public static Map<String, String> proposalUpdated(
            long proposalId,
            long userId,
            String incomingMessageId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            long categoryId,
            String beforeCategoryName,
            String beforeGroupingName,
            String afterCategoryName,
            String afterGroupingName,
            String txId) {
        String row = spendingRowJson(
                proposalId,
                userId,
                incomingMessageId,
                description,
                merchant,
                amountMinorUnits,
                currencyCode,
                categoryId);
        return entry(
                "expense_proposal",
                "u",
                row,
                row,
                txId,
                enrichmentSide(beforeCategoryName, beforeGroupingName),
                enrichmentSide(afterCategoryName, afterGroupingName));
    }

    public static Map<String, String> proposalDeleted(
            long proposalId,
            long userId,
            String incomingMessageId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            long categoryId,
            String txId) {
        String before = spendingRowJson(
                proposalId,
                userId,
                incomingMessageId,
                description,
                merchant,
                amountMinorUnits,
                currencyCode,
                categoryId);
        return entry("expense_proposal", "d", before, null, txId, null, null);
    }

    public static Map<String, String> expenseCreated(
            long expenseId,
            long userId,
            String incomingMessageId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            long categoryId,
            String categoryName,
            String groupingName,
            String txId) {
        String after = spendingRowJson(
                expenseId,
                userId,
                incomingMessageId,
                description,
                merchant,
                amountMinorUnits,
                currencyCode,
                categoryId);
        return entry("expense", "c", null, after, txId, null, enrichmentSide(categoryName, groupingName));
    }

    public static Map<String, String> expenseUpdated(
            long expenseId,
            long userId,
            String incomingMessageId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            long categoryId,
            String beforeCategoryName,
            String beforeGroupingName,
            String afterCategoryName,
            String afterGroupingName,
            String txId) {
        String row = spendingRowJson(
                expenseId,
                userId,
                incomingMessageId,
                description,
                merchant,
                amountMinorUnits,
                currencyCode,
                categoryId);
        return entry(
                "expense",
                "u",
                row,
                row,
                txId,
                enrichmentSide(beforeCategoryName, beforeGroupingName),
                enrichmentSide(afterCategoryName, afterGroupingName));
    }

    public static Map<String, String> expenseDeleted(
            long expenseId,
            long userId,
            String incomingMessageId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            long categoryId,
            String txId) {
        String before = spendingRowJson(
                expenseId,
                userId,
                incomingMessageId,
                description,
                merchant,
                amountMinorUnits,
                currencyCode,
                categoryId);
        return entry("expense", "d", before, null, txId, null, null);
    }

    public static Map<String, String> categoryUpdatedWithParent(
            long categoryId, long userId, long parentId, String beforeName, String afterName, String txId) {
        String before = categoryRowJson(categoryId, userId, parentId, beforeName);
        String after = categoryRowJson(categoryId, userId, parentId, afterName);
        return entry("category", "u", before, after, txId, null, null);
    }

    public static Map<String, String> categoryUpdatedWithoutParent(
            long categoryId, long userId, String beforeName, String afterName, String txId) {
        String before = categoryRowJsonNoParent(categoryId, userId, beforeName);
        String after = categoryRowJsonNoParent(categoryId, userId, afterName);
        return entry("category", "u", before, after, txId, null, null);
    }

    public static Map<String, String> snapshotRead(String table, String row, String txId) {
        return entry(table, "r", row, row, txId, null, null);
    }

    public static Map<String, String> unknownTable(String txId) {
        return entry("app_user", "c", null, "{\"id\":1}", txId, null, null);
    }

    /** A body with no {@code payload} field but still publishable — Redis XADD refuses an entry with none. */
    public static Map<String, String> withNoPayload() {
        return Map.of("enrichment", "{}");
    }

    private static Map<String, String> entry(
            String table,
            String op,
            String before,
            String after,
            String txId,
            String enrichmentBefore,
            String enrichmentAfter) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("payload", payloadJson(table, op, before, after, txId));
        if (enrichmentBefore != null || enrichmentAfter != null) {
            body.put(
                    "enrichment",
                    "{\"before\":%s,\"after\":%s}"
                            .formatted(
                                    enrichmentBefore == null ? "null" : enrichmentBefore,
                                    enrichmentAfter == null ? "null" : enrichmentAfter));
        }
        return body;
    }

    private static String payloadJson(String table, String op, String before, String after, String txId) {
        return """
                {"op":"%s","source":{"table":"%s","lsn":%d,"txId":"%s","ts_ms":%d},"before":%s,"after":%s}"""
                .formatted(
                        op,
                        table,
                        DEFAULT_LSN,
                        txId,
                        DEFAULT_TS_MS,
                        before == null ? "null" : before,
                        after == null ? "null" : after);
    }

    private static String spendingRowJson(
            long id,
            long userId,
            String incomingMessageId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            long categoryId) {
        return """
                {"id":%d,"user_id":%d,"category_id":%d,"description":"%s","merchant":%s,\
                "amount_minor_units":%d,"currency_code":"%s","incoming_message_id":%s}"""
                .formatted(
                        id,
                        userId,
                        categoryId,
                        description,
                        merchant == null ? "null" : "\"" + merchant + "\"",
                        amountMinorUnits,
                        currencyCode,
                        incomingMessageId == null ? "null" : "\"" + incomingMessageId + "\"");
    }

    private static String categoryRowJson(long id, long userId, long parentId, String name) {
        return """
                {"id":%d,"user_id":%d,"parent_id":%d,"name":"%s"}"""
                .formatted(id, userId, parentId, name);
    }

    private static String categoryRowJsonNoParent(long id, long userId, String name) {
        return """
                {"id":%d,"user_id":%d,"parent_id":null,"name":"%s"}""".formatted(id, userId, name);
    }

    private static String enrichmentSide(String categoryName, String groupingName) {
        if (categoryName == null && groupingName == null) {
            return null;
        }
        return """
                {"categoryName":%s,"groupingName":%s}"""
                .formatted(
                        categoryName == null ? "null" : "\"" + categoryName + "\"",
                        groupingName == null ? "null" : "\"" + groupingName + "\"");
    }
}
