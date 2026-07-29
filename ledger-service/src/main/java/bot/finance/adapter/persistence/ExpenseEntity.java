package bot.finance.adapter.persistence;

import bot.finance.domain.model.Expense;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("expense")
public record ExpenseEntity(
        @Id Long id,
        Long userId,
        Long categoryId,
        String description,
        String merchant,
        long amountMinorUnits,
        String currencyCode,
        Instant createdAt,
        Instant updatedAt) {

    public Expense toDomain() {
        Money money = new Money(amountMinorUnits, CurrencyCode.of(currencyCode));
        Optional<String> merchantOptional = Optional.ofNullable(merchant);
        if (id == null) {
            return Expense.newExpense(userId, categoryId, description, merchantOptional, money, createdAt);
        }
        return Expense.stored(id, userId, categoryId, description, merchantOptional, money, createdAt, updatedAt);
    }

    public static ExpenseEntity fromDomain(Expense expense) {
        return new ExpenseEntity(
                expense.id().orElse(null),
                expense.userId(),
                expense.categoryId(),
                expense.description(),
                expense.merchant().orElse(null),
                expense.money().minorUnits(),
                expense.money().currencyCode().code(),
                expense.createdAt(),
                expense.updatedAt());
    }
}
