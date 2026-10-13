package bot.finance.adapter.mcp;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.application.port.Logger;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes one DEBUG line per MCP tool call under this class's own logger, the line the evaluation runner matches a
 * case's expected calls against. It carries the caller and the arguments, so it is off unless enabled.
 */
final class McpCallLine {

    private McpCallLine() {}

    static void write(Logger log, JsonMapper jsonMapper, String tool, CallToolResult result, Object arguments) {
        log.debug(
                "mcp_call tool={} outcome={} userId={} message={} arguments={}",
                tool,
                Boolean.TRUE.equals(result.isError()) ? "rejected" : "ok",
                callerOrNone(),
                messageOrNone(),
                jsonMapper.writeValueAsString(arguments));
    }

    static Map<String, String> arguments(String... namesAndValues) {
        Map<String, String> arguments = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            arguments.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return arguments;
    }

    private static String callerOrNone() {
        try {
            return String.valueOf(AuthenticatedCaller.authenticatedUserId().userId());
        } catch (RuntimeException e) {
            return "none";
        }
    }

    private static String messageOrNone() {
        try {
            return AuthenticatedCaller.incomingMessageId().value();
        } catch (RuntimeException e) {
            return "none";
        }
    }
}
