package bot.finance.ai.common.fixtures;

import bot.finance.ai.domain.value.CategoryRow;
import bot.finance.ai.domain.value.CategoryRowChange;
import bot.finance.ai.domain.value.ChangeOperation;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.SpendingKind;
import bot.finance.ai.domain.value.SpendingRow;
import bot.finance.ai.domain.value.SpendingRowChange;
import java.util.Optional;

/** Builders of {@link bot.finance.ai.domain.value.RecordedChange} values, and the {@link SpendingRow} they hold. */
public final class RecordedChangeFixtures {

    public static final long DEFAULT_ROW_ID = 1L;
    public static final long DEFAULT_USER_ID = 10L;
    public static final String DEFAULT_MESSAGE_ID = "msg-1";
    public static final String DEFAULT_DESCRIPTION = "lunch";
    public static final long DEFAULT_AMOUNT_MINOR_UNITS = 1500L;
    public static final String DEFAULT_CURRENCY = "EUR";
    public static final long DEFAULT_CATEGORY_ID = 5L;
    public static final String DEFAULT_CATEGORY_NAME = "Groceries";
    public static final String DEFAULT_GROUPING_NAME = "Food";
    public static final long DEFAULT_PARENT_ID = 2L;
    public static final String DEFAULT_TRANSACTION_ID = "tx-1";

    private RecordedChangeFixtures() {}

    public static SpendingRow spendingRow() {
        return spendingRow(DEFAULT_ROW_ID, Optional.of(DEFAULT_MESSAGE_ID));
    }

    public static SpendingRow spendingRow(long id, Optional<String> incomingMessageId) {
        return new SpendingRow(
                id,
                DEFAULT_USER_ID,
                incomingMessageId,
                DEFAULT_DESCRIPTION,
                Optional.empty(),
                DEFAULT_AMOUNT_MINOR_UNITS,
                CurrencyCode.of(DEFAULT_CURRENCY),
                DEFAULT_CATEGORY_ID,
                Optional.of(DEFAULT_CATEGORY_NAME),
                Optional.of(DEFAULT_GROUPING_NAME));
    }

    public static SpendingRowChange proposalCreated() {
        return new SpendingRowChange(
                SpendingKind.PROPOSAL,
                ChangeOperation.CREATED,
                DEFAULT_TRANSACTION_ID,
                Optional.empty(),
                Optional.of(spendingRow()));
    }

    public static SpendingRowChange proposalUpdated() {
        SpendingRow row = spendingRow();
        return new SpendingRowChange(
                SpendingKind.PROPOSAL,
                ChangeOperation.UPDATED,
                DEFAULT_TRANSACTION_ID,
                Optional.of(row),
                Optional.of(row));
    }

    public static SpendingRowChange proposalDeleted() {
        return new SpendingRowChange(
                SpendingKind.PROPOSAL,
                ChangeOperation.DELETED,
                DEFAULT_TRANSACTION_ID,
                Optional.of(spendingRow()),
                Optional.empty());
    }

    public static SpendingRowChange expenseCreated() {
        return new SpendingRowChange(
                SpendingKind.EXPENSE,
                ChangeOperation.CREATED,
                DEFAULT_TRANSACTION_ID,
                Optional.empty(),
                Optional.of(spendingRow()));
    }

    public static SpendingRowChange expenseUpdated() {
        SpendingRow row = spendingRow();
        return new SpendingRowChange(
                SpendingKind.EXPENSE,
                ChangeOperation.UPDATED,
                DEFAULT_TRANSACTION_ID,
                Optional.of(row),
                Optional.of(row));
    }

    public static SpendingRowChange expenseDeleted() {
        return new SpendingRowChange(
                SpendingKind.EXPENSE,
                ChangeOperation.DELETED,
                DEFAULT_TRANSACTION_ID,
                Optional.of(spendingRow()),
                Optional.empty());
    }

    public static CategoryRow categoryRow() {
        return categoryRow(DEFAULT_CATEGORY_ID, Optional.of(DEFAULT_PARENT_ID), "Dining");
    }

    public static CategoryRow categoryRow(long id, Optional<Long> parentId, String name) {
        return new CategoryRow(id, DEFAULT_USER_ID, parentId, name);
    }

    public static CategoryRowChange categoryCreated() {
        return new CategoryRowChange(ChangeOperation.CREATED, Optional.empty(), Optional.of(categoryRow()));
    }

    public static CategoryRowChange categoryUpdated() {
        CategoryRow before = categoryRow();
        CategoryRow after = categoryRow(before.id(), before.parentId(), "Food");
        return new CategoryRowChange(ChangeOperation.UPDATED, Optional.of(before), Optional.of(after));
    }

    public static CategoryRowChange categoryDeleted() {
        return new CategoryRowChange(ChangeOperation.DELETED, Optional.of(categoryRow()), Optional.empty());
    }
}
