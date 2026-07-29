package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.Money;
import java.util.Optional;

public record NewExpense(
        String userExternalId, long categoryId, String description, Optional<String> merchant, Money money) {

    public NewExpense {
        if (userExternalId == null || userExternalId.isBlank()) {
            throw new InvalidUserException("new expense has no user external id");
        }
        if (categoryId <= 0) {
            throw new InvalidExpenseException("new expense has a non-positive category id");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidExpenseException("new expense has no description");
        }
        if (merchant == null) {
            throw new InvalidExpenseException("new expense has no merchant");
        }
        if (money == null) {
            throw new InvalidExpenseException("new expense has no money");
        }
        merchant = merchant.filter(m -> !m.isBlank());
    }
}
