package bot.finance.ai.adapter.ledger;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpRequest;

@Component
public class CallerTokenMcpRequestCustomizer implements McpSyncHttpClientRequestCustomizer {

    /**
     * The key {@link LedgerMcpConfiguration}'s {@code transportContextProvider} stores the turn's token under.
     */
    public static final String CALLER_TOKEN = "callerToken";

    @Override
    public void customize(HttpRequest.Builder builder, String method, URI endpoint, String body, McpTransportContext context) {
        // TODO: read CALLER_TOKEN out of context and set it verbatim, scheme included, as the Authorization
        // header; throw McpTransportException when the context holds none, so no request leaves unauthenticated.
    }

}
