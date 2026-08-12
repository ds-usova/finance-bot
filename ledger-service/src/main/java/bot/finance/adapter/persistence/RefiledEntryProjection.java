package bot.finance.adapter.persistence;

import bot.finance.application.dto.ExpenseEntry;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.Optional;

public record RefiledEntryProjection(
        long id,
        long categoryId,
        String description,
        String merchant,
        long amountMinorUnits,
        String currencyCode,
        Instant createdAt) {

    public ExpenseEntry toExpenseEntry(ExpenseStatus status) {
        return new ExpenseEntry(
                status,
                id,
                categoryId,
                description,
                Optional.ofNullable(merchant),
                new Money(amountMinorUnits, CurrencyCode.of(currencyCode)),
                createdAt);
    }
}
