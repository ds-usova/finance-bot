package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.Money;
import java.util.Optional;

public record CreateExpenseProposalCommand(
        AuthenticatedUserId userId,
        String categoryName,
        String groupingName,
        String description,
        Optional<String> merchant,
        Money money,
        IncomingMessageId incomingMessageId) {

    public CreateExpenseProposalCommand {
        if (userId == null) {
            throw new InvalidUserException("new expense proposal has no userId");
        }
        if (categoryName == null || categoryName.isBlank()) {
            throw new InvalidExpenseProposalException("new expense proposal has no category name");
        }
        if (groupingName == null || groupingName.isBlank()) {
            throw new InvalidExpenseProposalException("new expense proposal has no grouping name");
        }
        if (description == null || description.isBlank()) {
            throw new InvalidExpenseProposalException("new expense proposal has no description");
        }
        if (merchant == null) {
            throw new InvalidExpenseProposalException("new expense proposal has no merchant");
        }
        merchant = merchant.filter(m -> !m.isBlank());
        if (money == null) {
            throw new InvalidExpenseProposalException("new expense proposal has no money");
        }
        if (incomingMessageId == null) {
            throw new InvalidExpenseProposalException("new expense proposal has no incomingMessageId");
        }
    }
}
