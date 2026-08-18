package bot.finance.ai.common.fixtures;

import bot.finance.ai.domain.value.CategoryRef;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.RecordedStatus;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.StreamPosition;
import java.util.Optional;

/**
 * Builders of {@link SpendingRow}, {@link CategoryRef}, {@link RecordedStatus} and {@link StreamPosition}
 * values with named defaults, for the unit and integration steps.
 */
public final class SpendingFactFixtures {

    public static final long DEFAULT_EXPENSE_ID = 9001L;
    public static final long DEFAULT_USER_ID = 10L;
    public static final String DEFAULT_MESSAGE_ID = "msg-1";
    public static final String DEFAULT_DESCRIPTION = "lunch";
    public static final String DEFAULT_AMOUNT = "15.00";
    public static final String DEFAULT_CURRENCY = "EUR";
    public static final long DEFAULT_CATEGORY_ID = 5L;
    public static final String DEFAULT_CATEGORY_NAME = "Groceries";
    public static final long DEFAULT_GROUPING_ID = 2L;
    public static final String DEFAULT_GROUPING_NAME = "Food";
    public static final long DEFAULT_MS = 1_700_000_000_000L;
    public static final long DEFAULT_SEQ = 0L;

    private SpendingFactFixtures() {}

    public static CategoryRef category() {
        return category(DEFAULT_CATEGORY_ID, DEFAULT_CATEGORY_NAME);
    }

    public static CategoryRef category(long id, String name) {
        return new CategoryRef(id, name);
    }

    public static CategoryRef grouping() {
        return category(DEFAULT_GROUPING_ID, DEFAULT_GROUPING_NAME);
    }

    public static StreamPosition position() {
        return position(DEFAULT_MS, DEFAULT_SEQ);
    }

    public static StreamPosition position(long ms, long seq) {
        return new StreamPosition(ms, seq);
    }

    public static SpendingRow spendingRow() {
        return spendingRow(DEFAULT_EXPENSE_ID, Optional.of(DEFAULT_MESSAGE_ID));
    }

    public static SpendingRow spendingRow(long expenseId, Optional<String> incomingMessageId) {
        return new SpendingRow(
                expenseId,
                DEFAULT_USER_ID,
                incomingMessageId,
                DEFAULT_DESCRIPTION,
                Optional.empty(),
                DEFAULT_AMOUNT,
                CurrencyCode.of(DEFAULT_CURRENCY),
                category(),
                Optional.of(grouping()));
    }
}
