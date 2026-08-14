package bot.finance.common.boot;

import bot.finance.common.containers.WireMockSupport;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Boots the full application over a random HTTP port, reachable at {@code /mcp}, for the inbound MCP-tool
 * adapter test. Isolation comes from {@code @MockitoBean} on {@code CreateExpenseProposalPort} in the test
 * class, not from a framework slice - the same shape {@code GrpcAdapterTest} gives the AI Connector's gRPC
 * inbound adapter.
 *
 * <p>{@code @DynamicPropertySource} needs a static method inside a class body, which an annotation type
 * cannot declare, so the telegram bot API redirect to {@link WireMockSupport} - required because polling is on
 * by default - is a {@link DynamicPropertyRegistrar} bean instead, as {@code AiConnectorAdapterTest} does for
 * its own stub target.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TheWholeApplication
@Import(McpAdapterTest.TelegramStubTargetConfiguration.class)
public @interface McpAdapterTest {

    @TestConfiguration(proxyBeanMethods = false)
    class TelegramStubTargetConfiguration {

        @Bean
        DynamicPropertyRegistrar telegramApiUrl() {
            return registry -> registry.add("telegram.bot.api-url", () -> WireMockSupport.baseUrl() + "/bot");
        }
    }
}
