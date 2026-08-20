package bot.finance.common.boot;

import bot.finance.adapter.metrics.MicrometerOutboxMeters;
import bot.finance.adapter.metrics.MicrometerToolCallMeters;
import bot.finance.adapter.metrics.MicrometerTurnMeters;
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

/**
 * Boots the three Micrometer meter adapters behind the prometheus actuator endpoint on the management port, so a
 * test drives a meter port and reads what a scrape actually renders - the same shape
 * {@code CdcRecoveryEndpointTest} gives the recovery endpoint, with {@link TheSecurityChain} whose management
 * chain lets the scrape through.
 *
 * <p>Autoconfiguration is left on, since the web layer, the actuator and the {@code PrometheusMeterRegistry} the
 * adapters register into are all autoconfigured. What narrows this is the bean list, which component-scans
 * nothing, and the exclusions: without them a datasource and a Redis connection factory are built for beans no
 * scenario reaches, and the MCP server would demand tools this slice does not boot.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.ai.mcp.server.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"
        })
@SpringBootTest(
        classes = MetricsAdapterTest.MetricsAdapterConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public @interface MetricsAdapterTest {

    @EnableAutoConfiguration
    @TheSecurityChain
    @Import({
        MicrometerToolCallMeters.class,
        MicrometerTurnMeters.class,
        MicrometerOutboxMeters.class,
    })
    class MetricsAdapterConfiguration {}
}
