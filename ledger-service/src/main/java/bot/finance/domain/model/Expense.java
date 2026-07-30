package bot.finance.domain.model;

import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.Optional;

public final class Expense extends Entity {

    private final long userId;
    private final long categoryId;
    private final String description;
    private final String merchant;
    private final Money money;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Expense(
            Long id,
            long userId,
            long categoryId,
            String description,
            String merchant,
            Money money,
            Instant createdAt,
            Instant updatedAt) {
        super(id);
        // asserts, throwing InvalidExpenseException: a present, non-blank description; a present money;
        // a positive userId and categoryId; both instants present. The merchant Optional itself is
        // asserted in each factory, before it is unwrapped - it does not reach this constructor
        this.userId = userId;
        this.categoryId = categoryId;
        this.description = description;
        this.merchant = merchant;
        this.money = money;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Expense newExpense(
            long userId, long categoryId, String description, Optional<String> merchant, Money money, Instant now) {
        return new Expense(null, userId, categoryId, description, merchant.orElse(null), money, now, now);
    }

    public static Expense stored(
            long id,
            long userId,
            long categoryId,
            String description,
            Optional<String> merchant,
            Money money,
            Instant createdAt,
            Instant updatedAt) {
        return new Expense(id, userId, categoryId, description, merchant.orElse(null), money, createdAt, updatedAt);
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
