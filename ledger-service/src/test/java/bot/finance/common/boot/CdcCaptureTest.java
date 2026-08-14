package bot.finance.common.boot;

import bot.finance.LedgerServiceApplication;
import bot.finance.common.containers.PostgresContainers;
import bot.finance.common.containers.RedisContainers;
import bot.finance.common.containers.ToxiproxyContainers;
import bot.finance.common.containers.WireMockSupport;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application with capture switched on and {@link RedisContainers} wired in, for a system test
 * that drives the change stream end to end through the entry points a person actually reaches. A test of the
 * capture adapter itself takes {@link CdcAdapterTest}, which boots the adapter and not the application around
 * it. {@code cdc.enabled=true} overrides the {@code test} profile's own default, which otherwise keeps capture
 * off so a default-on engine never puts several polling system tests' contexts on one slot
 * ({@link PostgresContainers} is a JVM-wide singleton, same as this class's own reasoning in
 * {@code application-test.yaml}).
 *
 * <p>The slot name is deliberately left at the application's own default rather than fixed here: two capture
 * test classes both opening it against the shared {@link PostgresContainers} singleton would fight over it. A
 * class needing its own slot declares its own {@code @TestPropertySource(properties = "cdc.slot-name=...")}
 * beside this annotation, the same way a long-polling system test isolates itself with its own bot token
 * (see {@link bot.finance.common.stubs.TelegramTestBot} and the isolation rules in the testing conventions).
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
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@ImportTestcontainers(PostgresContainers.class)
@TestPropertySource(properties = "cdc.enabled=true")
@SpringBootTest(classes = LedgerServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CdcCaptureTest.CaptureTestConfiguration.class)
public @interface CdcCaptureTest {

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
