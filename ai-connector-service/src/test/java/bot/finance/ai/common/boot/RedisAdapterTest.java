package bot.finance.ai.common.boot;

import bot.finance.ai.adapter.logging.Slf4jLoggerFactory;
import bot.finance.ai.adapter.redis.ChangeStreamConfiguration;
import bot.finance.ai.adapter.redis.ChangeStreamConsumer;
import bot.finance.ai.adapter.redis.ChangeStreamEntryReader;
import bot.finance.ai.common.containers.RedisContainers;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.UUID;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Wires only the change-stream consumer slice against the real, containerized Redis — {@code ChangeStreamConfiguration},
 * {@link ChangeStreamConsumer}, {@link ChangeStreamEntryReader} and {@link Slf4jLoggerFactory}. Isolation comes
 * from {@code @MockitoBean} on {@code LearnMessageOutcomePort} in the test class, not from a framework slice —
 * the same shape {@link PersistenceAdapterTest} gives the persistence slice.
 * {@code spring.data.redis.url} and a stream key of this context's own are registered the same way
 * {@link AbstractMemorySystemTest} registers its own, so two contexts never share one key in one consumer group.
 * Skips when Docker is down.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@TestPropertySource(properties = {"memory.enabled=true", "ledger.change-stream.claim-idle=2s"})
@SpringBootTest(
        classes = {
            ChangeStreamConfiguration.class,
            ChangeStreamConsumer.class,
            ChangeStreamEntryReader.class,
            Slf4jLoggerFactory.class
        })
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
@Import(RedisAdapterTest.RedisPropertiesConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
public @interface RedisAdapterTest {

    @TestConfiguration(proxyBeanMethods = false)
    class RedisPropertiesConfiguration {

        @Bean
        DynamicPropertyRegistrar redisProperties() {
            String key = "ledger.cdc-" + UUID.randomUUID();
            return registry -> {
                registry.add("spring.data.redis.url", RedisContainers::redisUrl);
                registry.add("ledger.change-stream.key", () -> key);
            };
        }
    }
}
