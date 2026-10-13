package bot.finance.eval.observe;

import tools.jackson.databind.JsonNode;

/** One MCP tool call as the ledger's {@code mcp_call} line records it. */
public record McpCall(String tool, boolean accepted, String incomingMessageId, JsonNode arguments) {}
