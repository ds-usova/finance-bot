package bot.finance.ai.adapter.ledger;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpTransportException;
import java.net.URI;
import java.net.http.HttpRequest;
import org.springframework.stereotype.Component;

@Component
public class CallerTokenMcpRequestCustomizer implements McpSyncHttpClientRequestCustomizer {

    /**
     * The key {@link LedgerMcpConfiguration}'s {@code transportContextProvider} stores the turn's token under.
     */
    public static final String CALLER_TOKEN = "callerToken";

    @Override
    public void customize(
            HttpRequest.Builder builder, String method, URI endpoint, String body, McpTransportContext context) {
        Object token = context.get(CALLER_TOKEN);
        if (token == null) {
            throw new McpTransportException("No caller token in the MCP transport context");
        }
        builder.header("Authorization", token.toString());
    }
}
