package bot.finance.eval.observe;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/** One call the ledger took during a turn, over MCP or gRPC, as its {@code turn_call} line records it. */
public record TurnCall(
        @JsonProperty("tool") String operation, boolean accepted, String incomingMessageId, JsonNode arguments) {}
