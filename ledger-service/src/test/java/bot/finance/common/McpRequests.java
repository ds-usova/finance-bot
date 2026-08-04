package bot.finance.common;

public final class McpRequests {

    /** The {@code Accept} header the MCP server requires on every {@code /mcp} POST. */
    public static final String ACCEPT_HEADER = "application/json, text/event-stream";

    private McpRequests() {}

    public static String initialize() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "method": "initialize",
                  "params": {
                    "protocolVersion": "2025-06-18",
                    "capabilities": {},
                    "clientInfo": { "name": "system-test", "version": "1.0.0" }
                  }
                }
                """;
    }

    public static String toolsList() {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 3,
                  "method": "tools/list"
                }
                """;
    }

    public static String createExpenseProposal(
            String category, String grouping, String description, String merchant, String amount, String currencyCode) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "method": "tools/call",
                  "params": {
                    "name": "create_expense_proposal",
                    "arguments": {
                      "category": %s,
                      "grouping": %s,
                      "description": %s,
                      "merchant": %s,
                      "amount": %s,
                      "currencyCode": %s
                    }
                  }
                }
                """
                .formatted(
                        jsonString(category),
                        jsonString(grouping),
                        jsonString(description),
                        jsonString(merchant),
                        jsonString(amount),
                        jsonString(currencyCode));
    }

    public static String listCategories(String grouping) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "method": "tools/call",
                  "params": {
                    "name": "list_categories",
                    "arguments": {
                      "grouping": %s
                    }
                  }
                }
                """
                .formatted(jsonString(grouping));
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
