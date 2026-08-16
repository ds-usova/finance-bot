package bot.finance.ai.adapter.persistence;

import java.time.Instant;
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
        Instant updatedAt) {}
