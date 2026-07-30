package bot.finance.adapter.mcp;

import bot.finance.adapter.security.AuthenticatedCallerUtils;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidExpenseProposalException;
import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.AuthenticatedUserId;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CreateExpenseProposalMcpTool {

    private final CreateExpenseProposalPort createExpenseProposalPort;
    private final JsonMapper jsonMapper;
    private final Logger log;

    public CreateExpenseProposalMcpTool(
            CreateExpenseProposalPort createExpenseProposalPort, JsonMapper jsonMapper, LoggerFactory loggerFactory) {
        this.createExpenseProposalPort = createExpenseProposalPort;
        this.jsonMapper = jsonMapper;
        this.log = loggerFactory.getLogger(CreateExpenseProposalMcpTool.class);
    }

    @McpTool(name = "create_expense_proposal", description = "Records a new expense proposal for the caller")
    public CallToolResult createExpenseProposal(
            @McpToolParam(description = "the category's name - one filed under a grouping, never a grouping")
                    String category,
            @McpToolParam(
                            required = false,
                            description = "the grouping's name, optional - only to break a tie between categories "
                                    + "sharing the same name")
                    String parentCategory,
            @McpToolParam(description = "what was bought") String description,
            @McpToolParam(
                            required = false,
                            description = "who it was bought from, optional - null or blank is none")
                    String merchant,
            @McpToolParam(description = "the amount in the currency's minor units, required") Long amountMinorUnits,
            @McpToolParam(description = "ISO 4217, three letters") String currencyCode) {
        CreateExpenseProposalToolRequest request = new CreateExpenseProposalToolRequest(
                category, parentCategory, description, merchant, amountMinorUnits, currencyCode);
        try {
            AuthenticatedUserId userId = AuthenticatedCallerUtils.authenticatedUserId();
            ExpenseProposal stored =
                    createExpenseProposalPort.create(ExpenseProposalToolUtils.toCommand(request, userId));
            CreateExpenseProposalToolResponse response =
                    ExpenseProposalToolUtils.toResponse(stored, request.category());
            return CallToolResult.builder()
                    .addTextContent(jsonMapper.writeValueAsString(response))
                    .build();
        } catch (InvalidExpenseProposalException | InvalidUserException | InvalidMoneyException e) {
            return rejected(e, "invalid request: " + e.getMessage());
        } catch (InvalidCategoryException e) {
            return rejected(e, e.getMessage());
        } catch (EntityNotFoundException e) {
            return rejected(e, "the user is unknown");
        } catch (PersistenceFailedException e) {
            return rejected(e, "the proposal could not be stored");
        } catch (RuntimeException e) {
            return rejected(e, "the proposal could not be created");
        }
    }

    private CallToolResult rejected(RuntimeException e, String message) {
        log.warn("rejected create_expense_proposal call: {}", e.getClass().getSimpleName());
        return CallToolResult.builder().isError(true).addTextContent(message).build();
    }
}
