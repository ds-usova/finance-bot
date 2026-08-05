package bot.finance.ai.adapter.ledger;

import bot.finance.ai.adapter.grpc.CallerTokenContext;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.Map;
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
            if (LEDGER_CONNECTION.equals(name)) {
                builder.httpRequestCustomizer(callerTokenMcpRequestCustomizer);
            }
        };
    }

    @Bean
    McpClientCustomizer<McpClient.SyncSpec> callerTokenContextCustomizer() {
        return (name, spec) -> {
            if (LEDGER_CONNECTION.equals(name)) {
                spec.transportContextProvider(() -> CallerTokenContext.callerToken()
                        .<McpTransportContext>map(token ->
                                McpTransportContext.create(Map.of(CallerTokenMcpRequestCustomizer.CALLER_TOKEN, token)))
                        .orElse(McpTransportContext.EMPTY));
            }
        };
    }
}
