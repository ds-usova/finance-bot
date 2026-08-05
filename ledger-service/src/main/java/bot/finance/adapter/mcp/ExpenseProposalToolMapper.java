package bot.finance.adapter.mcp;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.MessageReference;
import bot.finance.domain.value.Money;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ExpenseProposalToolMapper {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d{1,18}(\\.\\d{1,4})?$");

    private ExpenseProposalToolMapper() {}

    public static CreateExpenseProposalCommand toCommand(
            CreateExpenseProposalToolRequest request, AuthenticatedUserId userId, MessageReference reference) {
        if (request == null) {
            throw new InvalidExpenseProposalException("expense proposal request must be present");
        }
        if (request.amount() == null) {
            throw new InvalidExpenseProposalException("expense proposal request has no amount");
        }
        String strippedAmount = request.amount().strip();
        if (!AMOUNT_PATTERN.matcher(strippedAmount).matches()) {
            throw new InvalidExpenseProposalException("amount must be digits with an optional dot, like 7200 or 12.50");
        }
        if (request.grouping() == null || request.grouping().isBlank()) {
            throw new InvalidExpenseProposalException("expense proposal request has no grouping");
        }
        Optional<String> merchant = blankToEmpty(request.merchant());
        Money money = Money.ofMajorUnits(new BigDecimal(strippedAmount), CurrencyCode.of(request.currencyCode()));
        return new CreateExpenseProposalCommand(
                userId, request.category(), request.grouping(), request.description(), merchant, money, reference);
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
                proposal.money().amount().toPlainString(),
                proposal.money().currencyCode().code(),
                proposal.createdAt());
    }
}
