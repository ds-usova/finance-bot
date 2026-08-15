package bot.finance.ai.common.boot;

import bot.finance.ai.adapter.ai.AiExpenseRecordingAdapter;
import bot.finance.ai.adapter.ai.ChatClientConfiguration;
import bot.finance.ai.adapter.ledger.CallerTokenMcpRequestCustomizer;
import bot.finance.ai.adapter.ledger.LedgerMcpConfiguration;
import bot.finance.ai.adapter.ledger.LedgerToolFailureProcessor;
import bot.finance.ai.adapter.logging.Slf4jLoggerFactory;
import bot.finance.ai.common.containers.WireMockSupport;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.httpclient.autoconfigure.StreamableHttpHttpClientTransportAutoConfiguration;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots {@link AiExpenseRecordingAdapter}, {@link ChatClientConfiguration}, {@link LedgerMcpConfiguration},
 * {@link CallerTokenMcpRequestCustomizer}, {@link LedgerToolFailureProcessor} and Spring AI's OpenAI, MCP client,
 * streamable-HTTP transport and tool-callback autoconfigurations — no gRPC server, no other adapter.
 * {@code spring.ai.openai.base-url} and the ledger connection's {@code url} are redirected to
 * {@link WireMockSupport}'s dynamic port through {@link WireMockUrlConfiguration}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@SpringBootTest(
        classes = {
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
@Import(WireMockUrlConfiguration.class)
public @interface AiAdapterTest {}
