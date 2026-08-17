package bot.finance.ai.adapter.persistence;

import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.ExampleOutcome;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("recorded_expense")
public record RecordedExpenseEntity(
        @Id Long id,
        long messageId,
        long userId,
        Long proposalId,
        Long expenseId,
        String description,
        String merchant,
        long amountMinorUnits,
        String currencyCode,
        long categoryId,
        String categoryName,
        String groupingName,
        String status,
        String movedInTx,
        Instant updatedAt) {

    public ExampleExpense toExampleExpense() {
        CurrencyCode currency = CurrencyCode.of(currencyCode);
        return new ExampleExpense(
                description,
                currency.toDecimal(amountMinorUnits),
                currency,
                Optional.ofNullable(categoryName),
                Optional.ofNullable(groupingName),
                ExampleOutcome.valueOf(status));
    }
}
