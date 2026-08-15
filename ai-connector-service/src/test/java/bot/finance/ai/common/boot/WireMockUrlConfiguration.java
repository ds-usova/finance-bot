package bot.finance.ai.common.boot;

import bot.finance.ai.common.containers.WireMockSupport;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Points the application's OpenAI client and its MCP connection to the ledger at {@link WireMockSupport}'s
 * dynamic port. Neither can live in {@code application-test.yaml}, since the stub server binds a port that is
 * only known at runtime.
 */
@TestConfiguration(proxyBeanMethods = false)
public class WireMockUrlConfiguration {

    @Bean
    DynamicPropertyRegistrar wireMockBaseUrl() {
        return registry -> {
            registry.add("spring.ai.openai.base-url", WireMockSupport::openAiBaseUrl);
            registry.add("spring.ai.mcp.client.streamable-http.connections.ledger.url", WireMockSupport::baseUrl);
        };
    }
}
