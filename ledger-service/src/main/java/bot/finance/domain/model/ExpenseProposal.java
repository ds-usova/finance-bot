package bot.finance.domain.model;

import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.Optional;

public final class ExpenseProposal extends Entity {

    private final long userId;
    private final long categoryId;
    private final String description;
    private final String merchant;
    private final Money money;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ExpenseProposal(
            Long id,
            long userId,
            long categoryId,
            String description,
            Optional<String> merchant,
            Money money,
            Instant createdAt,
            Instant updatedAt) {
        super(id);
        // rejects an absent or blank description, an absent merchant Optional, an absent money, a
        // non-positive user id, a non-positive category id, and an absent created_at or updated_at,
        // each with InvalidExpenseProposalException
        this.userId = userId;
        this.categoryId = categoryId;
        this.description = description;
        this.merchant = merchant.orElse(null);
        this.money = money;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ExpenseProposal newExpenseProposal(
            long userId, long categoryId, String description, Optional<String> merchant, Money money, Instant now) {
        return new ExpenseProposal(null, userId, categoryId, description, merchant, money, now, now);
    }

    public static ExpenseProposal stored(
            long id,
            long userId,
            long categoryId,
            String description,
            Optional<String> merchant,
            Money money,
            Instant createdAt,
            Instant updatedAt) {
        return new ExpenseProposal(id, userId, categoryId, description, merchant, money, createdAt, updatedAt);
    }

    public long userId() {
        return userId;
    }

    public long categoryId() {
        return categoryId;
    }

    public String description() {
        return description;
    }

    public Optional<String> merchant() {
        return Optional.ofNullable(merchant);
    }

    public Money money() {
        return money;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
