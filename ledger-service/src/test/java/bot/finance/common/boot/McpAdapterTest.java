package bot.finance.common.boot;

import static org.mockito.Mockito.mock;

import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.adapter.mcp.CreateExpenseProposalMcpTool;
import bot.finance.adapter.mcp.ListCategoriesMcpTool;
import bot.finance.adapter.mcp.SummarizeSpendingMcpTool;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.adapter.security.JwksController;
import bot.finance.adapter.security.RecoverySecretFilter;
import bot.finance.adapter.security.SecurityConfiguration;
import bot.finance.adapter.security.TokenSigningKeys;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.ListCategoriesPort;
import bot.finance.application.port.SummarizeSpendingPort;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots the MCP tools over a random HTTP port, reachable at {@code /mcp}, with the security chain that mints and
 * validates their tokens. Isolation comes from {@code @MockitoBean} on the inbound port in the test class, not
 * from a framework slice - the same shape {@link AiConnectorAdapterTest} gives the AI Connector's gRPC inbound
 * adapter.
 *
 * <p>Autoconfiguration is left on, since the MCP server, the web layer and the security filter chains are all
 * autoconfigured; what narrows this is the bean list, which component-scans nothing. No Telegram poll loop, no
 * gRPC client, no capture engine, and no repository.
 *
 * <p>Flyway is off because nothing here reads a row, so no database has to be reachable and no container has to
 * be started.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
@SpringBootTest(
        classes = McpAdapterTest.McpAdapterConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public @interface McpAdapterTest {

    @EnableAutoConfiguration
    @Import({
        ListCategoriesMcpTool.class,
        CreateExpenseProposalMcpTool.class,
        SummarizeSpendingMcpTool.class,
        SecurityConfiguration.class,
        RecoverySecretFilter.class,
        JwksController.class,
        AccessTokenMinter.class,
        TokenSigningKeys.class,
        Slf4jLoggerFactory.class,
    })
    class McpAdapterConfiguration {

        /**
         * All three ports, because all three tools are registered with the one MCP server. A test class replaces
         * the one it drives with its own {@code @MockitoBean} and leaves the other two unused.
         */
        @Bean
        ListCategoriesPort listCategoriesPort() {
            return mock(ListCategoriesPort.class);
        }

        @Bean
        CreateExpenseProposalPort createExpenseProposalPort() {
            return mock(CreateExpenseProposalPort.class);
        }

        @Bean
        SummarizeSpendingPort summarizeSpendingPort() {
            return mock(SummarizeSpendingPort.class);
        }
    }
}
