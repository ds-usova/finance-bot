package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.Money;
import java.util.Optional;

public record CreateExpenseProposalCommand(
        AuthenticatedUserId userId,
        String categoryName,
        Optional<String> parentCategoryName,
        String description,
        Optional<String> merchant,
        Money money) {

    public CreateExpenseProposalCommand {
        // TODO: reject an absent userId with InvalidUserException; reject an absent, empty or
        // whitespace-only categoryName and an absent parentCategoryName Optional with
        // InvalidExpenseProposalException; normalize a present-but-blank parentCategoryName to
        // Optional.empty()
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
