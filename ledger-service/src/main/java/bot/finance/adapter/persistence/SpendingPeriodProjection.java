package bot.finance.adapter.persistence;

import bot.finance.domain.value.SpendingPeriod;
import java.time.Instant;
import java.time.LocalDate;

public record SpendingPeriodProjection(LocalDate periodStart, LocalDate periodEnd, Instant createdAt) {

    public SpendingPeriod toPeriod() {
        return new SpendingPeriod(periodStart, periodEnd);
    }
}
