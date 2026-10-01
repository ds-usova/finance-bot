package bot.finance.ai.common.boot;

import bot.finance.ai.adapter.logging.Slf4jLoggerFactory;
import bot.finance.ai.adapter.redis.ChangeStreamConfiguration;
import bot.finance.ai.adapter.redis.ChangeStreamConsumer;
import bot.finance.ai.adapter.redis.ChangeStreamEntryHandler;
import bot.finance.ai.adapter.redis.ChangeStreamEntryReader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Wires only the change-stream consumer slice against the real, containerized Redis — {@code ChangeStreamConfiguration},
 * {@link ChangeStreamConsumer}, {@link ChangeStreamEntryHandler}, {@link ChangeStreamEntryReader} and
 * {@link Slf4jLoggerFactory}. Isolation comes from {@code @MockitoBean} on {@code LearnMessageOutcomePort} in the
 * test class, not from a framework slice — the same shape {@link PersistenceAdapterTest} gives the persistence
 * slice. {@code spring.data.redis.url} and a stream key of this context's own come from
 * {@link RedisPropertiesConfiguration}. Skips when Docker is down.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@TestPropertySource(properties = {"memory.enabled=true", "ledger.change-stream.claim-idle=2s"})
@SpringBootTest(
        classes = {
            ChangeStreamConfiguration.class,
            ChangeStreamConsumer.class,
            ChangeStreamEntryHandler.class,
            ChangeStreamEntryReader.class,
            Slf4jLoggerFactory.class
        })
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
@Import(RedisPropertiesConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
public @interface RedisAdapterTest {}
