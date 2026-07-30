package bot.finance.domain.model;

import bot.finance.domain.exception.InvalidExpenseException;
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
            Optional<String> merchant,
            Money money,
            Instant createdAt,
            Instant updatedAt) {
        super(id);
        if (description == null || description.isBlank()) {
            throw new InvalidExpenseException("description must be present");
        }
        if (merchant == null) {
            throw new InvalidExpenseException("merchant must be present");
        }
        if (money == null) {
            throw new InvalidExpenseException("money must be present");
        }
        if (userId <= 0) {
            throw new InvalidExpenseException("user id must be positive");
        }
        if (categoryId <= 0) {
            throw new InvalidExpenseException("category id must be positive");
        }
        if (createdAt == null) {
            throw new InvalidExpenseException("created at must be present");
        }
        if (updatedAt == null) {
            throw new InvalidExpenseException("updated at must be present");
        }
        this.userId = userId;
        this.categoryId = categoryId;
        this.description = description;
        this.merchant = merchant.orElse(null);
        this.money = money;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Expense newExpense(
            long userId, long categoryId, String description, Optional<String> merchant, Money money, Instant now) {
        return new Expense(null, userId, categoryId, description, merchant, money, now, now);
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
        return new Expense(id, userId, categoryId, description, merchant, money, createdAt, updatedAt);
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
