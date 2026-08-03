package bot.finance.adapter.persistence;

import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("expense_proposal")
public record ExpenseProposalEntity(
        @Id Long id,
        Long userId,
        Long categoryId,
        String description,
        String merchant,
        long amountMinorUnits,
        String currencyCode,
        UUID messageReference,
        Instant createdAt,
        Instant updatedAt) {

    public ExpenseProposal toDomain() {
        return ExpenseProposal.stored(
                id,
                userId,
                categoryId,
                description,
                Optional.ofNullable(merchant),
                new Money(amountMinorUnits, CurrencyCode.of(currencyCode)),
                new MessageReference(messageReference),
                createdAt,
                updatedAt);
    }

    // The timestamp columns' microsecond precision does not round-trip nanosecond-precision
    // instants: the driver rounds rather than truncates. Truncating here removes the
    // sub-microsecond remainder so the stored value is exact.
    public static ExpenseProposalEntity fromDomain(ExpenseProposal proposal) {
        return new ExpenseProposalEntity(
                proposal.id().orElse(null),
                proposal.userId(),
                proposal.categoryId(),
                proposal.description(),
                proposal.merchant().orElse(null),
                proposal.money().minorUnits(),
                proposal.money().currencyCode().code(),
                proposal.messageReference().value(),
                proposal.createdAt().truncatedTo(ChronoUnit.MICROS),
                proposal.updatedAt().truncatedTo(ChronoUnit.MICROS));
    }
}
