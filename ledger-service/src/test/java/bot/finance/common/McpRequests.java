package bot.finance.common;

public final class McpRequests {

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

    public static String createExpenseProposal(
            String category,
            String parentCategory,
            String description,
            String merchant,
            Long amountMinorUnits,
            String currencyCode) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": 2,
                  "method": "tools/call",
                  "params": {
                    "name": "create_expense_proposal",
                    "arguments": {
                      "category": %s,
                      "parentCategory": %s,
                      "description": %s,
                      "merchant": %s,
                      "amountMinorUnits": %s,
                      "currencyCode": %s
                    }
                  }
                }
                """
                .formatted(
                        jsonString(category),
                        jsonString(parentCategory),
                        jsonString(description),
                        jsonString(merchant),
                        amountMinorUnits == null ? "null" : amountMinorUnits.toString(),
                        jsonString(currencyCode));
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
