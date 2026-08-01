package bot.finance.ai.adapter.ledger;

import bot.finance.ai.application.dto.ProposedExpense;
import bot.finance.ai.application.port.ExpenseProposalPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(LedgerMcpProperties.class)
public class McpExpenseProposalAdapter implements ExpenseProposalPort {

    private final LedgerMcpProperties properties;

    public McpExpenseProposalAdapter(LedgerMcpProperties properties) {
        this.properties = properties;
    }

    @Override
    public void propose(ProposedExpense expense) {
        // Opens an MCP client over Streamable HTTP against properties.url(), carrying
        // `Authorization: Bearer <CallerTokenUtils.callerToken()>` on every request, invokes
        // create_expense_proposal with category, parentCategory (when present), description,
        // amountMinorUnits and currencyCode, and closes it. A tool result flagged isError becomes a refusal; a
        // transport failure or a missing caller token becomes an unreachable ledger — both as
        // ExpenseProposalFailedException (D34, D35).
    }
}
