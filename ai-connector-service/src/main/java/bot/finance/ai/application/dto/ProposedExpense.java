package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.Money;

import java.util.Optional;

public record ProposedExpense(
        String categoryName, Optional<String> parentCategoryName, String description, Money amount) {

    public ProposedExpense {
        if (categoryName == null || categoryName.isBlank()) {
            throw new InvalidValueException("Category name must not be null or blank");
        }
        if (parentCategoryName == null) {
            throw new InvalidValueException(
                    "Parent category name must not be null; use Optional.empty() when absent");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidValueException("Description must not be null or blank");
        }
        if (amount == null) {
            throw new InvalidValueException("Amount must not be null");
        }
    }
}
