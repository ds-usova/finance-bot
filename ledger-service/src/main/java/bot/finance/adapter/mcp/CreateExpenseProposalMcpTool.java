package bot.finance.adapter.mcp;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.*;
import bot.finance.domain.model.ExpenseProposal;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.MessageReference;
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
            @McpToolParam(description = "the category's name - one filed under a grouping") String category,
            @McpToolParam(
                            description = "the grouping the category is filed under, exactly as "
                                    + "`list_categories` was asked for it")
                    String grouping,
            @McpToolParam(description = "what was bought") String description,
            @McpToolParam(required = false, description = "who it was bought from, optional - null or blank is none")
                    String merchant,
            @McpToolParam(
                            description =
                                    "the amount exactly as the message writes it, in the currency's main unit - 7200 "
                                            + "for 7200 HUF, 12.50 for 12.50 EUR. Digits, and at most one dot for "
                                            + "the decimals. Never convert it, never group the digits.")
                    String amount,
            @McpToolParam(description = "ISO 4217, three letters") String currencyCode) {
        CreateExpenseProposalToolRequest request =
                new CreateExpenseProposalToolRequest(category, grouping, description, merchant, amount, currencyCode);

        log.debug("Received create_expense_proposal call: {}", request);

        try {
            AuthenticatedUserId userId = AuthenticatedCaller.authenticatedUserId();
            MessageReference reference = AuthenticatedCaller.messageReference();

            ExpenseProposal stored =
                    createExpenseProposalPort.create(ExpenseProposalToolMapper.toCommand(request, userId, reference));
            CreateExpenseProposalToolResponse response =
                    ExpenseProposalToolMapper.toResponse(stored, request.category());

            log.debug("create_expense_proposal call succeeded: {}", response);
            return CallToolResult.builder()
                    .addTextContent(jsonMapper.writeValueAsString(response))
                    .build();
        } catch (InvalidExpenseProposalException | InvalidUserException | InvalidMoneyException e) {
            return rejected(e, "invalid request: " + e.getMessage());
        } catch (InvalidCategoryException | InvalidGroupingException e) {
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
        log.warn("rejected create_expense_proposal call: {} {}", e.getClass().getSimpleName(), e.getMessage());
        return CallToolResult.builder().isError(true).addTextContent(message).build();
    }
}
