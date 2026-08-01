package bot.finance.ai.adapter.ledger;

import bot.finance.ai.adapter.grpc.CallerTokenUtils;
import bot.finance.ai.application.dto.ProposedExpense;
import bot.finance.ai.application.port.ExpenseProposalPort;
import bot.finance.ai.domain.exception.ExpenseProposalFailedException;
import bot.finance.ai.domain.exception.ExpenseProposalFailedException.Reason;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.Map;

@Component
@EnableConfigurationProperties(LedgerMcpProperties.class)
public class McpExpenseProposalAdapter implements ExpenseProposalPort {

    private static final String CREATE_EXPENSE_PROPOSAL = "create_expense_proposal";

    private final LedgerMcpProperties properties;

    public McpExpenseProposalAdapter(LedgerMcpProperties properties) {
        this.properties = properties;
    }

    @Override
    public void propose(ProposedExpense expense) {
        String token = CallerTokenUtils.callerToken()
                .orElseThrow(() -> new ExpenseProposalFailedException(
                        "No caller token held in context", Reason.UNREACHABLE));

        McpClientTransport transport = HttpClientStreamableHttpTransport.builder(properties.url())
                // The token travels exactly as the caller sent it, scheme included: it is opaque text this
                // service never parses (D21).
                .requestBuilder(HttpRequest.newBuilder().header("Authorization", token))
                .build();

        try (McpSyncClient client = McpClient.sync(transport).build()) {
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(CREATE_EXPENSE_PROPOSAL, arguments(expense), null));
            if (Boolean.TRUE.equals(result.isError())) {
                throw new ExpenseProposalFailedException("Ledger refused the expense proposal", Reason.REFUSED);
            }
        } catch (ExpenseProposalFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ExpenseProposalFailedException("Ledger could not be reached", Reason.UNREACHABLE, e);
        }
    }

    private static Map<String, Object> arguments(ProposedExpense expense) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("category", expense.categoryName());
        expense.parentCategoryName().ifPresent(parentCategory -> arguments.put("parentCategory", parentCategory));
        arguments.put("description", expense.description());
        arguments.put("amountMinorUnits", expense.amount().minorUnits());
        arguments.put("currencyCode", expense.amount().currencyCode().code());
        return arguments;
    }
}
