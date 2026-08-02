package bot.finance.domain.model;

import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import java.time.Instant;
import java.util.Optional;

public final class ExpenseProposal extends Entity {

    private final long userId;
    private final long categoryId;
    private final String description;
    private final String merchant;
    private final Money money;
    private final MessageReference messageReference;
    private final Instant createdAt;
    private final Instant updatedAt;

    private ExpenseProposal(
            Long id,
            long userId,
            long categoryId,
            String description,
            Optional<String> merchant,
            Money money,
            MessageReference messageReference,
            Instant createdAt,
            Instant updatedAt) {
        super(id);
        if (description == null || description.isBlank()) {
            throw new InvalidExpenseProposalException("description must be present");
        }
        if (merchant == null) {
            throw new InvalidExpenseProposalException("merchant must be present");
        }
        if (money == null) {
            throw new InvalidExpenseProposalException("money must be present");
        }
        if (userId <= 0) {
            throw new InvalidExpenseProposalException("user id must be positive");
        }
        if (categoryId <= 0) {
            throw new InvalidExpenseProposalException("category id must be positive");
        }
        if (messageReference == null) {
            throw new InvalidExpenseProposalException("message reference must be present");
        }
        if (createdAt == null) {
            throw new InvalidExpenseProposalException("created at must be present");
        }
        if (updatedAt == null) {
            throw new InvalidExpenseProposalException("updated at must be present");
        }
        this.userId = userId;
        this.categoryId = categoryId;
        this.description = description;
        this.merchant = merchant.orElse(null);
        this.money = money;
        this.messageReference = messageReference;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ExpenseProposal newExpenseProposal(
            long userId,
            long categoryId,
            String description,
            Optional<String> merchant,
            Money money,
            MessageReference messageReference,
            Instant now) {
        return new ExpenseProposal(null, userId, categoryId, description, merchant, money, messageReference, now, now);
    }

    public static ExpenseProposal stored(
            long id,
            long userId,
            long categoryId,
            String description,
            Optional<String> merchant,
            Money money,
            MessageReference messageReference,
            Instant createdAt,
            Instant updatedAt) {
        return new ExpenseProposal(
                id, userId, categoryId, description, merchant, money, messageReference, createdAt, updatedAt);
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

    public MessageReference messageReference() {
        return messageReference;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
