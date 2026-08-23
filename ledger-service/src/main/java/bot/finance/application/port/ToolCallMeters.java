package bot.finance.application.port;

/**
 * Counts an MCP tool call by its outcome, so a rejection rate is visible without reading logs.
 */
public interface ToolCallMeters {

    void countOk(String tool);

    void countRejected(String tool, String reason);
}
