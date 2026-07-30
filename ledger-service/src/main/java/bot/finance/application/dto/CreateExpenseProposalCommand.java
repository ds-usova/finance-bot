package bot.finance.application.dto;

import bot.finance.domain.value.Money;
import java.util.Optional;

public record CreateExpenseProposalCommand(
        String userExternalId, long categoryId, String description, Optional<String> merchant, Money money) {

    public CreateExpenseProposalCommand {
        // rejects an absent or blank external id with InvalidUserException; rejects a category id
        // that is not positive, an absent or blank description, an absent merchant Optional, and an
        // absent money with InvalidExpenseProposalException;
        // normalizes a present-but-blank merchant to Optional.empty()
    }
}
