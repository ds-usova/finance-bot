package bot.finance.adapter.mcp;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import java.util.Optional;

public final class ExpenseProposalToolUtils {

    private ExpenseProposalToolUtils() {}

    public static CreateExpenseProposalCommand toCommand(
            CreateExpenseProposalToolRequest request, AuthenticatedUserId userId, MessageReference reference) {
        if (request == null) {
            throw new InvalidExpenseProposalException("expense proposal request must be present");
        }
        if (request.amountMinorUnits() == null) {
            throw new InvalidExpenseProposalException("expense proposal request has no amountMinorUnits");
        }
        Optional<String> parentCategory = blankToEmpty(request.parentCategory());
        Optional<String> merchant = blankToEmpty(request.merchant());
        Money money = new Money(request.amountMinorUnits(), CurrencyCode.of(request.currencyCode()));
        return new CreateExpenseProposalCommand(
                userId, request.category(), parentCategory, request.description(), merchant, money, reference);
    }

    private static Optional<String> blankToEmpty(String value) {
        return Optional.ofNullable(value).filter(v -> !v.isBlank());
    }

    public static CreateExpenseProposalToolResponse toResponse(ExpenseProposal proposal, String categoryName) {
        return new CreateExpenseProposalToolResponse(
                proposal.id().orElseThrow(),
                categoryName,
                proposal.description(),
                proposal.merchant().orElse(null),
                proposal.money().minorUnits(),
                proposal.money().currencyCode().code(),
                proposal.createdAt());
    }
}
