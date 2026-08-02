package bot.finance.ai.common;

import bot.finance.ai.adapter.ai.AiExpenseRecordingAdapter;
import bot.finance.ai.adapter.ai.ChatClientConfiguration;
import bot.finance.ai.adapter.ledger.CallerTokenMcpRequestCustomizer;
import bot.finance.ai.adapter.ledger.LedgerMcpConfiguration;
import bot.finance.ai.adapter.ledger.LedgerToolFailureProcessor;
import bot.finance.ai.adapter.logging.Slf4jLoggerFactory;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.httpclient.autoconfigure.StreamableHttpHttpClientTransportAutoConfiguration;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Boots {@link AiExpenseRecordingAdapter}, {@link ChatClientConfiguration}, {@link LedgerMcpConfiguration},
 * {@link CallerTokenMcpRequestCustomizer}, {@link LedgerToolFailureProcessor} and Spring AI's OpenAI, MCP client,
 * streamable-HTTP transport and tool-callback autoconfigurations — no gRPC server, no other adapter.
 * {@code spring.ai.openai.base-url} and the ledger connection's {@code url} are redirected to
 * {@link WireMockSupport}'s dynamic port through a {@link DynamicPropertyRegistrar} bean, because
 * {@code @DynamicPropertySource} needs a static method inside a class body, which an annotation type cannot
 * declare.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@SpringBootTest(classes = {
        AiExpenseRecordingAdapter.class,
        ChatClientConfiguration.class,
        LedgerMcpConfiguration.class,
        CallerTokenMcpRequestCustomizer.class,
        LedgerToolFailureProcessor.class,
        Slf4jLoggerFactory.class
})
@ImportAutoConfiguration({
        OpenAiChatAutoConfiguration.class,
        ChatClientAutoConfiguration.class,
        ToolCallingAutoConfiguration.class,
        McpClientAutoConfiguration.class,
        StreamableHttpHttpClientTransportAutoConfiguration.class,
        McpToolCallbackAutoConfiguration.class
})
@Import(AiAdapterTest.WireMockBaseUrlConfiguration.class)
public @interface AiAdapterTest {

    @TestConfiguration(proxyBeanMethods = false)
    class WireMockBaseUrlConfiguration {

        @Bean
        DynamicPropertyRegistrar wireMockBaseUrl() {
            return registry -> {
                registry.add("spring.ai.openai.base-url", WireMockSupport::openAiBaseUrl);
                registry.add(
                        "spring.ai.mcp.client.streamable-http.connections.ledger.url", WireMockSupport::baseUrl);
            };
        }

    }

}
