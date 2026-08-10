package bot.finance.adapter.persistence;

import bot.finance.domain.model.SpendingQuery;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.SpendingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("spending_query")
public record SpendingQueryEntity(
        @Id Long id,
        Long userId,
        String incomingMessageId,
        LocalDate periodStart,
        LocalDate periodEnd,
        Instant createdAt) {

    public SpendingQuery toDomain() {
        return SpendingQuery.stored(
                id,
                userId,
                new SpendingPeriod(periodStart, periodEnd),
                new IncomingMessageId(incomingMessageId),
                createdAt);
    }

    // The timestamp column's microsecond precision does not round-trip nanosecond-precision
    // instants: the driver rounds rather than truncates. Truncating here removes the
    // sub-microsecond remainder so the stored value is exact.
    public static SpendingQueryEntity fromDomain(SpendingQuery query) {
        return new SpendingQueryEntity(
                query.id().orElse(null),
                query.userId(),
                query.incomingMessageId().value(),
                query.period().from(),
                query.period().to(),
                query.createdAt().truncatedTo(ChronoUnit.MICROS));
    }
}
