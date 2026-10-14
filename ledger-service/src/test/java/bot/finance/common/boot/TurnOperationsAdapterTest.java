package bot.finance.common.boot;

import bot.finance.adapter.aiconnector.LedgerTurnEndGrpcService;
import bot.finance.adapter.aiconnector.LedgerTurnGrpcService;
import bot.finance.adapter.logging.LoggingConfiguration;
import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.adapter.logging.TurnCallLog;
import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.adapter.security.AccessTokenProperties;
import bot.finance.adapter.security.TokenSigningKeys;
import bot.finance.adapter.security.TokenSigningProperties;
import bot.finance.adapter.security.TurnOperationsSecurityConfiguration;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.EndTurnPort;
import bot.finance.application.port.SummarizeSpendingPort;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Boots the ledger's gRPC server with the turn operations behind the security interceptor that guards them, on
 * a random port. A test reaches the server on {@code GrpcServerLifecycle.getPort()} and calls it through
 * {@code TurnOperationCalls}. Isolation comes from {@code @MockitoBean} on the three inbound ports.
 *
 * <p>Autoconfiguration is left on, since the gRPC server, its health and reflection services and the gRPC
 * security builder are all autoconfigured. What narrows this is the bean list, which component-scans nothing,
 * and the exclusions: without them a datasource and a Redis connection factory are built for beans no scenario
 * reaches, and the MCP server would demand tools this slice does not boot.
 *
 * <p>{@link AccessTokenMinter} is listed so a test mints the tokens it calls with; the signing keys behind it are
 * the ones the interceptor verifies against.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.ai.mcp.server.enabled=false",
            "spring.grpc.server.port=0",
            "turn-call-log.enabled=true",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"
        })
@MockitoBean(types = {CreateExpenseProposalPort.class, SummarizeSpendingPort.class, EndTurnPort.class})
@SpringBootTest(
        classes = TurnOperationsAdapterTest.TurnOperationsAdapterConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public @interface TurnOperationsAdapterTest {

    @EnableAutoConfiguration
    @EnableConfigurationProperties({AccessTokenProperties.class, TokenSigningProperties.class})
    @Import({
        LedgerTurnGrpcService.class,
        LedgerTurnEndGrpcService.class,
        TurnOperationsSecurityConfiguration.class,
        TurnCallLog.class,
        LoggingConfiguration.class,
        TokenSigningKeys.class,
        AccessTokenMinter.class,
        Slf4jLoggerFactory.class,
    })
    class TurnOperationsAdapterConfiguration {}
}
