package bot.finance.adapter.persistence;

import bot.finance.domain.model.Expense;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.IncomingMessageId;
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
        String incomingMessageId,
        Instant createdAt,
        Instant updatedAt,
        String status) {

    public Expense toDomain() {
        Money money = new Money(amountMinorUnits, CurrencyCode.of(currencyCode));
        Optional<String> merchantOptional = Optional.ofNullable(merchant);
        ExpenseStatus expenseStatus = ExpenseStatus.valueOf(status);
        Optional<IncomingMessageId> messageId =
                Optional.ofNullable(incomingMessageId).map(IncomingMessageId::of);
        if (id == null) {
            if (expenseStatus == ExpenseStatus.PENDING) {
                return Expense.newProposal(
                        userId, categoryId, description, merchantOptional, money, messageId.orElse(null), createdAt);
            }
            return Expense.newExpense(userId, categoryId, description, merchantOptional, money, createdAt);
        }
        return Expense.stored(
                id,
                userId,
                categoryId,
                description,
                merchantOptional,
                money,
                expenseStatus,
                messageId,
                createdAt,
                updatedAt);
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
                expense.incomingMessageId().map(IncomingMessageId::value).orElse(null),
                expense.createdAt(),
                expense.updatedAt(),
                expense.status().name());
    }
}
