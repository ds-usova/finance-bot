package bot.finance.ai.adapter.ledger;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpTransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallerTokenMcpRequestCustomizerTest {

    private final CallerTokenMcpRequestCustomizer customizer = new CallerTokenMcpRequestCustomizer();

    @Nested
    @DisplayName("customize(builder, method, uri, body, context)")
    class Customize {

        @Test
        @DisplayName("when the transport context holds the turn's token, scheme included - then the built request carries it as the Authorization header, byte for byte")
        void whenContextHoldsToken_thenBuiltRequestCarriesItAsAuthorizationHeader() {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://ledger.local/mcp"));
            McpTransportContext context = McpTransportContext.create(
                    Map.of(CallerTokenMcpRequestCustomizer.CALLER_TOKEN, "Bearer caller-token-1"));

            customizer.customize(builder, "POST", URI.create("https://ledger.local/mcp"), "{}", context);

            HttpRequest request = builder.build();
            assertThat(request.headers().firstValue("Authorization")).contains("Bearer caller-token-1");
        }

        @Test
        @DisplayName("when the transport context is empty - then it throws McpTransportException and sets no header")
        void whenContextIsEmpty_thenThrowsMcpTransportExceptionAndSetsNoHeader() {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create("https://ledger.local/mcp"));

            assertThatThrownBy(() -> customizer.customize(
                    builder, "POST", URI.create("https://ledger.local/mcp"), "{}", McpTransportContext.EMPTY))
                    .isInstanceOf(McpTransportException.class);

            HttpRequest request = builder.build();
            assertThat(request.headers().firstValue("Authorization")).isEmpty();
        }
    }
}
