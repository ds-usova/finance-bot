package bot.finance.common.boot;

import bot.finance.adapter.mcp.CreateExpenseProposalMcpTool;
import bot.finance.adapter.mcp.ListCategoriesMcpTool;
import bot.finance.adapter.mcp.SummarizeSpendingMcpTool;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.adapter.security.JwksController;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the MCP tools over a random HTTP port, reachable at {@code /mcp}, with {@link TheSecurityChain} that
 * mints and validates their tokens. Isolation comes from {@code @MockitoBean} on the inbound port in the test
 * class, not from a framework slice - the same shape {@link AiConnectorAdapterTest} gives the AI Connector's
 * gRPC inbound adapter.
 *
 * <p>Autoconfiguration is left on, since the MCP server, the web layer and the security filter chains are all
 * autoconfigured. What narrows this is the bean list, which component-scans nothing, and the two exclusions:
 * without them a datasource and a Redis connection factory are built for beans no scenario reaches.
 *
 * <p>{@link JwksController} is not optional. The MCP token decoder fetches the signing keys over HTTP from this
 * application's own {@code /.well-known/jwks.json}, so without it every call fails inside the decoder and
 * surfaces as a 500 the logs say nothing about.
 *
 * <p>All three ports are mocked because all three tools register with the one MCP server. A test class declares
 * its own {@code @MockitoBean} for the port it drives, which replaces the one here and is reset per test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@TestPropertySource(
        properties = "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@MockitoBean(types = {ListCategoriesPort.class, CreateExpenseProposalPort.class, SummarizeSpendingPort.class})
@SpringBootTest(
        classes = McpAdapterTest.McpAdapterConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public @interface McpAdapterTest {

    @EnableAutoConfiguration
    @TheSecurityChain
    @Import({
        ListCategoriesMcpTool.class,
        CreateExpenseProposalMcpTool.class,
        SummarizeSpendingMcpTool.class,
        JwksController.class,
        AccessTokenMinter.class,
    })
    class McpAdapterConfiguration {}
}
