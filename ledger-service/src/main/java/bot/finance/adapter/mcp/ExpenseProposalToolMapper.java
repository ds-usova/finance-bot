package bot.finance.adapter.mcp;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.Money;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ExpenseProposalToolMapper {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("^\\d{1,18}(\\.\\d{1,4})?$");

    private ExpenseProposalToolMapper() {}

    public static CreateExpenseProposalCommand toCommand(
            CreateExpenseProposalToolRequest request, AuthenticatedUserId userId, IncomingMessageId reference) {
        if (request == null) {
            throw new InvalidExpenseException("expense proposal request must be present");
        }
        if (request.amount() == null) {
            throw new InvalidExpenseException("expense proposal request has no amount");
        }
        String strippedAmount = request.amount().strip();
        if (!AMOUNT_PATTERN.matcher(strippedAmount).matches()) {
            throw new InvalidExpenseException("amount must be digits with an optional dot, like 7200 or 12.50");
        }
        if (request.grouping() == null || request.grouping().isBlank()) {
            throw new InvalidExpenseException("expense proposal request has no grouping");
        }
        Optional<String> merchant = blankToEmpty(request.merchant());
        Money money = Money.ofMajorUnits(new BigDecimal(strippedAmount), CurrencyCode.of(request.currencyCode()));
        return new CreateExpenseProposalCommand(
                userId, request.category(), request.grouping(), request.description(), merchant, money, reference);
    }

    private static Optional<String> blankToEmpty(String value) {
        return Optional.ofNullable(value).filter(v -> !v.isBlank());
    }

    public static CreateExpenseProposalToolResponse toResponse(Expense proposal, String categoryName) {
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
