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
        long expenseId,
        String description,
        String merchant,
        String amount,
        String currencyCode,
        long categoryId,
        String categoryName,
        Long groupingId,
        String groupingName,
        String status,
        long appliedMs,
        long appliedSeq,
        Instant updatedAt) {

    public ExampleExpense toExampleExpense() {
        return new ExampleExpense(
                description,
                amount,
                CurrencyCode.of(currencyCode),
                Optional.ofNullable(categoryName),
                Optional.ofNullable(groupingName),
                ExampleOutcome.valueOf(status));
    }
}
