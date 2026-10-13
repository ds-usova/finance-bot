package bot.finance.eval.observe;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/** One MCP tool call as the ledger's {@code mcp_call} line records it. */
public record TurnCall(
        @JsonProperty("tool") String operation, boolean accepted, String incomingMessageId, JsonNode arguments) {}
