package bot.finance.adapter.persistence;

import bot.finance.application.dto.ExpenseEntry;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.Optional;

public record SpendingRowProjection(
        long id,
        long userId,
        String incomingMessageId,
        String status,
        String description,
        String merchant,
        long amountMinorUnits,
        String currencyCode,
        Instant createdAt,
        long categoryId,
        String categoryName,
        Long groupingId,
        String groupingName) {

    public ExpenseStatus expenseStatus() {
        return ExpenseStatus.valueOf(status);
    }

    public ExpenseEntry toExpenseEntry() {
        return new ExpenseEntry(
                expenseStatus(),
                id,
                categoryId,
                description,
                Optional.ofNullable(merchant),
                new Money(amountMinorUnits, CurrencyCode.of(currencyCode)),
                createdAt);
    }
}
