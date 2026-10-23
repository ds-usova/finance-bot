package bot.finance.adapter.mcp;

import bot.finance.adapter.logging.TurnCallLog;
import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.application.dto.SummarizeSpendingCommand;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.application.port.ToolCallMeters;
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
    private final ToolCallMeters toolCallMeters;
    private final Logger log;
    private final TurnCallLog turnCallLog;

    public SummarizeSpendingMcpTool(
            SummarizeSpendingPort summarizeSpendingPort,
            JsonMapper jsonMapper,
            ToolCallMeters toolCallMeters,
            TurnCallLog turnCallLog,
            LoggerFactory loggerFactory) {
        this.summarizeSpendingPort = summarizeSpendingPort;
        this.jsonMapper = jsonMapper;
        this.toolCallMeters = toolCallMeters;
        this.turnCallLog = turnCallLog;
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
        CallToolResult result = summarize(from, to);
        turnCallLog.write(
                "summarize_spending",
                TurnCallLog.Via.MCP,
                !Boolean.TRUE.equals(result.isError()),
                TurnCallLog.arguments("from", from, "to", to));
        return result;
    }

    private CallToolResult summarize(String from, String to) {
        log.debug("Received summarize_spending call: {} {}", from, to);

        try {
            AuthenticatedUserId userId = AuthenticatedCaller.authenticatedUserId();
            IncomingMessageId reference = AuthenticatedCaller.incomingMessageId();

            SpendingPeriod period =
                    summarizeSpendingPort.summarize(new SummarizeSpendingCommand(userId, reference, from, to));
            SummarizeSpendingToolResponse response = new SummarizeSpendingToolResponse(
                    period.from().toString(), period.to().toString());

            log.debug("summarize_spending call succeeded: {}", response);
            toolCallMeters.countOk("summarize_spending");
            return CallToolResult.builder()
                    .addTextContent(jsonMapper.writeValueAsString(response))
                    .build();
        } catch (RuntimeException e) {
            return rejected(e, ToolRefusal.ofSpendingSummary(e));
        }
    }

    private CallToolResult rejected(RuntimeException e, ToolRefusal refusal) {
        log.warn("rejected summarize_spending call: {} {}", e.getClass().getSimpleName(), e.getMessage());
        toolCallMeters.countRejected("summarize_spending", refusal.meterReason(e));
        return CallToolResult.builder()
                .isError(true)
                .addTextContent(refusal.message())
                .build();
    }
}
