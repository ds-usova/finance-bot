package bot.finance.adapter.persistence;

import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.Optional;
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
        Instant createdAt,
        Instant updatedAt) {

    public ExpenseProposal toDomain() {
        Money money = new Money(amountMinorUnits, CurrencyCode.of(currencyCode));
        Optional<String> merchantOptional = Optional.ofNullable(merchant);
        if (id == null) {
            return ExpenseProposal.newExpenseProposal(
                    userId, categoryId, description, merchantOptional, money, createdAt);
        }
        return ExpenseProposal.stored(
                id, userId, categoryId, description, merchantOptional, money, createdAt, updatedAt);
    }

    public static ExpenseProposalEntity fromDomain(ExpenseProposal proposal) {
        return new ExpenseProposalEntity(
                proposal.id().orElse(null),
                proposal.userId(),
                proposal.categoryId(),
                proposal.description(),
                proposal.merchant().orElse(null),
                proposal.money().minorUnits(),
                proposal.money().currencyCode().code(),
                proposal.createdAt(),
                proposal.updatedAt());
    }
}
