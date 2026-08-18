package bot.finance.common.boot;

import bot.finance.adapter.cdc.ChangeEventPublisher;
import bot.finance.adapter.cdc.ChangeStreamConfiguration;
import bot.finance.adapter.cdc.ChangeStreamMeters;
import bot.finance.adapter.cdc.ChangeStreamReader;
import bot.finance.adapter.cdc.ChangeStreamRecovery;
import bot.finance.adapter.cdc.ReplicationSlotMonitor;
import bot.finance.adapter.logging.Slf4jLoggerFactory;
import bot.finance.adapter.persistence.DatabaseConnectionDetails;
import bot.finance.adapter.persistence.LedgerEventOutbox;
import bot.finance.adapter.persistence.ReplicationCatalogue;
import bot.finance.adapter.persistence.SpendingEventRenderer;
import bot.finance.adapter.redis.RedisChangeStreamWriter;
import bot.finance.common.containers.RedisContainers;
import bot.finance.common.containers.ToxiproxyContainers;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * The capture adapter's beans, and the two the Data JDBC slice does not carry - a meter registry and a Redis
 * template. Shared by {@link CdcAdapterTest} and {@link CdcAdapterTestOnItsOwnDatabase}, which differ only in
 * which database they point at.
 *
 * <p>Redis is reached through {@link ToxiproxyContainers} for every class, not only the one that cuts the
 * connection: a class pointed at the container directly could not be handed the proxy later without rebuilding
 * its context, and a class that never touches the toxic cannot tell the difference.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({
    ChangeStreamConfiguration.class,
    ChangeStreamReader.class,
    ChangeStreamRecovery.class,
    ReplicationSlotMonitor.class,
    ChangeStreamMeters.class,
    ChangeEventPublisher.class,
    ReplicationCatalogue.class,
    DatabaseConnectionDetails.class,
    LedgerEventOutbox.class,
    SpendingEventRenderer.class,
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
        return RedisContainers.template(RedisContainers.connectionFactoryFor(ToxiproxyContainers.proxiedRedisUrl()));
    }
}
