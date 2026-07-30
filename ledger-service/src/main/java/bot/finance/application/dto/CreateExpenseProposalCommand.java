package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.Money;
import java.util.Optional;

public record CreateExpenseProposalCommand(
        String userExternalId, long categoryId, String description, Optional<String> merchant, Money money) {

    public CreateExpenseProposalCommand {
        if (userExternalId == null || userExternalId.isBlank()) {
            throw new InvalidUserException("new expense proposal has no user external id");
        }
        if (categoryId <= 0) {
            throw new InvalidExpenseProposalException("new expense proposal has a non-positive category id");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidExpenseProposalException("new expense proposal has no description");
        }
        if (merchant == null) {
            throw new InvalidExpenseProposalException("new expense proposal has no merchant");
        }
        if (money == null) {
            throw new InvalidExpenseProposalException("new expense proposal has no money");
        }
        merchant = merchant.filter(m -> !m.isBlank());
    }
}
