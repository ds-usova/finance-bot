package bot.finance.ai.common;

import bot.finance.ai.adapter.ledger.LedgerMcpProperties;
import bot.finance.ai.adapter.ledger.McpExpenseProposalAdapter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Boots {@link McpExpenseProposalAdapter} and {@link LedgerMcpProperties} with {@code ledger.mcp.url} pointed
 * at {@link WireMockSupport}'s dynamic port, over a real MCP client — nothing is mocked. A
 * {@code @ConfigurationProperties} record is not registered by listing it in {@code @SpringBootTest(classes =
 * …)}, hence {@code @EnableConfigurationProperties} here.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@SpringBootTest(classes = McpExpenseProposalAdapter.class)
@EnableConfigurationProperties(LedgerMcpProperties.class)
@Import(LedgerAdapterTest.LedgerMcpUrlConfiguration.class)
public @interface LedgerAdapterTest {

    @TestConfiguration(proxyBeanMethods = false)
    class LedgerMcpUrlConfiguration {

        @Bean
        DynamicPropertyRegistrar ledgerMcpUrl() {
            return registry -> registry.add("ledger.mcp.url", WireMockSupport::baseUrl);
        }
    }
}
