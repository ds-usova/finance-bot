package bot.finance.ai.adapter.ledger;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerMcpConfiguration {

    private static final String LEDGER_CONNECTION = "ledger";

    @Bean
    McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> callerTokenTransportCustomizer(
            CallerTokenMcpRequestCustomizer callerTokenMcpRequestCustomizer) {
        return (name, builder) -> {
            // TODO: when name equals LEDGER_CONNECTION, apply
            // builder.httpRequestCustomizer(callerTokenMcpRequestCustomizer).
        };
    }

    @Bean
    McpClientCustomizer<McpClient.SyncSpec> callerTokenContextCustomizer() {
        return (name, spec) -> {
            // TODO: when name equals LEDGER_CONNECTION, apply spec.transportContextProvider(...), a supplier
            // reading CallerTokenUtils.callerToken() and returning
            // McpTransportContext.create(Map.of(CallerTokenMcpRequestCustomizer.CALLER_TOKEN, token)), or
            // McpTransportContext.EMPTY when the turn holds none.
        };
    }

}
