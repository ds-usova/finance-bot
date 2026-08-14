package bot.finance.common.boot;

import bot.finance.common.containers.PostgresContainers;
import bot.finance.common.containers.RedisContainers;
import bot.finance.common.containers.ToxiproxyContainers;
import bot.finance.common.containers.WireMockSupport;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots the full application with capture switched on and {@link RedisContainers} wired in, for a system test
 * that drives the change stream end to end through the entry points a person actually reaches. A test of the
 * capture adapter itself takes {@link CdcAdapterTest}, which boots the adapter and not the application around
 * it. {@code cdc.enabled=true} overrides the {@code test} profile's own default, which otherwise keeps capture
 * off so a default-on engine never puts several polling system tests' contexts on one slot
 * ({@link PostgresContainers} is a JVM-wide singleton, same as this class's own reasoning in
 * {@code application-test.yaml}).
 *
 * <p>The slot name and the stream key are left at the application's own defaults, named below as {@link #SLOT_NAME}
 * and {@link #STREAM_KEY}. Every class carrying this annotation therefore shares one context, one engine and one
 * slot, which is the shape production runs in. Postgres allows a slot one active consumer, so a class that
 * overrode either would be founding a second engine rather than joining this one — and what separates two
 * classes' entries on the shared stream is the user each creates, read back through
 * {@link bot.finance.common.fixtures.ChangeStreamEntries#entriesOnFor}.
 *
 * <p>{@code @DynamicPropertySource} needs a static method inside a class body, which an annotation type cannot
 * declare, so both the Redis URL and the telegram stub redirect — required because polling is on by default —
 * are {@link DynamicPropertyRegistrar} beans instead, the same shape {@code McpAdapterTest} gives its own.
 *
 * <p>Redis is reached through {@link ToxiproxyContainers} rather than directly, for every capture test and not
 * only the ones that cut the connection. A registrar bean is applied during the context refresh, while a test
 * class's own {@code @DynamicPropertySource} runs before it, so the two write to the same map and this one wins
 * — a class that pointed itself at the proxy would silently be handed the direct URL instead, and its outage
 * would do nothing. Routing everything through the proxy removes the conflict: a test that never touches the
 * toxic cannot tell the difference.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TheWholeApplication
@TestPropertySource(properties = "cdc.enabled=true")
@Import(CdcCaptureTest.CaptureTestConfiguration.class)
public @interface CdcCaptureTest {

    /** The {@code cdc.slot-name} the application defaults to, and therefore the one every capture test runs on. */
    String SLOT_NAME = "finance_ledger_cdc";

    /** The {@code cdc.stream-key} the application defaults to, and therefore the one every capture test reads. */
    String STREAM_KEY = "ledger.cdc";

    /** The {@code cdc.recovery-secret} the {@code test} profile configures. */
    String RECOVERY_SECRET = "default-test-recovery-secret";

    @TestConfiguration(proxyBeanMethods = false)
    class CaptureTestConfiguration {

        @Bean
        DynamicPropertyRegistrar redisUrl() {
            return registry -> registry.add("spring.data.redis.url", ToxiproxyContainers::proxiedRedisUrl);
        }

        @Bean
        DynamicPropertyRegistrar telegramApiUrl() {
            return registry -> registry.add("telegram.bot.api-url", () -> WireMockSupport.baseUrl() + "/bot");
        }
    }
}
