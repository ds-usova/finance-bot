package bot.finance.adapter.mcp;

import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

@Component
public class CreateExpenseProposalMcpTool {

    private final CreateExpenseProposalPort createExpenseProposalPort;
    private final Logger log;

    public CreateExpenseProposalMcpTool(CreateExpenseProposalPort createExpenseProposalPort, LoggerFactory loggerFactory) {
        this.createExpenseProposalPort = createExpenseProposalPort;
        this.log = loggerFactory.getLogger(CreateExpenseProposalMcpTool.class);
    }

    @McpTool(name = "create_expense_proposal", description = "Records a new expense proposal for the caller")
    public CallToolResult createExpenseProposal(CreateExpenseProposalToolRequest request) {
        // reads the caller's identity through AuthenticatedCallerUtils, maps the request through
        // ExpenseProposalToolUtils, calls CreateExpenseProposalPort, and converts every failure into a tool
        // error result per the design's Failures table, logging each rejection at WARN with the failure kind
        // and neither the arguments nor the token
        return null;
    }
}
