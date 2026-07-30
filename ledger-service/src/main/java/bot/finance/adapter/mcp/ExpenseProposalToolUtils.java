package bot.finance.adapter.mcp;

import bot.finance.application.dto.CreateExpenseProposalCommand;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.AuthenticatedUserId;

public final class ExpenseProposalToolUtils {

    private ExpenseProposalToolUtils() {}

    public static CreateExpenseProposalCommand toCommand(
            CreateExpenseProposalToolRequest request, AuthenticatedUserId userId) {
        // builds the command from the request and the caller's identity, lifting a null or blank
        // parentCategory and merchant to Optional.empty() and building Money from amountMinorUnits and
        // CurrencyCode; rejects an absent request before constructing the command
        return null;
    }

    public static CreateExpenseProposalToolResponse toResponse(ExpenseProposal proposal, String categoryName) {
        // maps the stored proposal onto the tool's result record
        return null;
    }
}
