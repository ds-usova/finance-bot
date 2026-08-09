package bot.finance.adapter.mcp;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.application.dto.SummarizeSpendingCommand;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.exception.InvalidSpendingQueryException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.SpendingPeriod;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class SummarizeSpendingMcpTool {

    private final SummarizeSpendingPort summarizeSpendingPort;
    private final JsonMapper jsonMapper;
    private final Logger log;

    public SummarizeSpendingMcpTool(
            SummarizeSpendingPort summarizeSpendingPort, JsonMapper jsonMapper, LoggerFactory loggerFactory) {
        this.summarizeSpendingPort = summarizeSpendingPort;
        this.jsonMapper = jsonMapper;
        this.log = loggerFactory.getLogger(SummarizeSpendingMcpTool.class);
    }

    @McpTool(
            name = "summarize_spending",
            description = "Answers the caller's question about what they spent over a period. The totals are put "
                    + "in front of the caller directly; they are not returned to you, and you never state an "
                    + "amount yourself.")
    public CallToolResult summarizeSpending(
            @McpToolParam(description = "the first day of the period, inclusive, as YYYY-MM-DD") String from,
            @McpToolParam(description = "the last day of the period, inclusive, as YYYY-MM-DD") String to) {
        log.debug("Received summarize_spending call: {} {}", from, to);

        try {
            AuthenticatedUserId userId = AuthenticatedCaller.authenticatedUserId();
            IncomingMessageId reference = AuthenticatedCaller.incomingMessageId();

            SpendingPeriod period =
                    summarizeSpendingPort.summarize(new SummarizeSpendingCommand(userId, reference, from, to));
            SummarizeSpendingToolResponse response = new SummarizeSpendingToolResponse(
                    period.from().toString(), period.to().toString());

            log.debug("summarize_spending call succeeded: {}", response);
            return CallToolResult.builder()
                    .addTextContent(jsonMapper.writeValueAsString(response))
                    .build();
        } catch (InvalidSpendingPeriodException e) {
            return rejected(e, e.getMessage());
        } catch (InvalidSpendingQueryException | InvalidUserException e) {
            return rejected(e, "invalid request: " + e.getMessage());
        } catch (EntityNotFoundException e) {
            return rejected(e, "the user is unknown");
        } catch (PersistenceFailedException e) {
            return rejected(e, "the summary could not be recorded");
        } catch (RuntimeException e) {
            return rejected(e, "the spending could not be summarized");
        }
    }

    private CallToolResult rejected(RuntimeException e, String message) {
        log.warn("rejected summarize_spending call: {} {}", e.getClass().getSimpleName(), e.getMessage());
        return CallToolResult.builder().isError(true).addTextContent(message).build();
    }
}
