package bot.finance.application.dto;

import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.Optional;

public record ExpenseEntry(
        ExpenseStatus status,
        long id,
        long categoryId,
        String description,
        Optional<String> merchant,
        Money money,
        Instant createdAt) {}
