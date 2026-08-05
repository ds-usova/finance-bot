package bot.finance.adapter.mcp;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SummarizeSpendingPort;
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
        // logs the call at debug, reads the caller and the message reference off the token, invokes
        // SummarizeSpendingPort, serializes SummarizeSpendingToolResponse, and renders every failure as an
        // isError result logged at warn
        return null;
    }
}
