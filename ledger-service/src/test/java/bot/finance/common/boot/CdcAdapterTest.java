package bot.finance.common.boot;

import bot.finance.adapter.cdc.CategoryNameResolver;
import bot.finance.adapter.cdc.ChangeEventPublisher;
import bot.finance.adapter.cdc.ChangeStreamConfiguration;
import bot.finance.adapter.cdc.ChangeStreamMeters;
import bot.finance.adapter.cdc.ChangeStreamReader;
import bot.finance.adapter.cdc.ChangeStreamRecovery;
import bot.finance.adapter.cdc.ReplicationSlotMonitor;
import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.adapter.persistence.CategoryRowReader;
import bot.finance.adapter.persistence.DatabaseConnectionDetails;
import bot.finance.adapter.persistence.ReplicationCatalogue;
import bot.finance.adapter.redis.RedisChangeStreamWriter;
import bot.finance.common.containers.PostgresContainers;
import bot.finance.common.containers.RedisContainers;
import bot.finance.common.containers.ToxiproxyContainers;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Composed annotation for the change-capture adapter's own integration tests.
 *
 * <p>Boots the Data JDBC slice against the containerized Postgres, plus the capture adapter's beans and a Redis
 * template pointed at the real Redis. Nothing else: no web layer, no Telegram poll loop, no MCP server, no gRPC
 * client, no security chains. A test needing the whole application uses {@link CdcCaptureTest} instead.
 *
 * <p>{@code Propagation.NOT_SUPPORTED} switches off the slice's rolled-back transaction. A capture test writes a
 * row and waits for the embedded engine, reading the write-ahead log on its own replication connection, to offer
 * it — and an uncommitted row never reaches that log. Each test therefore commits, and cleans up after itself.
 *
 * <p>Redis is reached through {@link ToxiproxyContainers} for every class, not only the one that cuts the
 * connection: a class pointed at the container directly could not be given the proxy later without rebuilding
 * its context, and a class that never touches the toxic cannot tell the difference.
 *
 * <p>The slot name is left at the application's own default. Two capture classes opening one slot against the
 * shared {@link PostgresContainers} singleton fight over it, so a class declares its own
 * {@code @TestPropertySource(properties = "cdc.slot-name=...")}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@ImportTestcontainers(PostgresContainers.class)
@DataJdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(CdcAdapterTest.CaptureAdapterConfiguration.class)
public @interface CdcAdapterTest {

    @TestConfiguration(proxyBeanMethods = false)
    @Import({
        ChangeStreamConfiguration.class,
        ChangeStreamReader.class,
        ChangeStreamRecovery.class,
        ReplicationSlotMonitor.class,
        ChangeStreamMeters.class,
        ChangeEventPublisher.class,
        CategoryNameResolver.class,
        ReplicationCatalogue.class,
        DatabaseConnectionDetails.class,
        CategoryRowReader.class,
        RedisChangeStreamWriter.class,
        Slf4jLoggerFactory.class,
    })
    public class CaptureAdapterConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return RedisContainers.template(
                    RedisContainers.connectionFactoryFor(ToxiproxyContainers.proxiedRedisUrl()));
        }
    }
}
