package bot.finance.ai.common.boot;

import bot.finance.ai.common.containers.PostgresContainers;
import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import bot.finance.ai.common.stubs.LedgerJwksStubs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A system test with the memory on, against the real, containerized database and Redis, and a purge interval
 * short enough to observe within a test. Publishes {@link CallerTokens}' key set before each test, and truncates
 * {@code incoming_message} and {@code stream_entry_failure} after it so one class's rows never leak into the
 * next (@{@code recorded_expense} cascades from {@code incoming_message}). Skips when Docker is down.
 *
 * <p>{@code spring.data.redis.url} and a stream key of this context's own, {@link #changeStreamKey}, come from
 * {@link RedisPropertiesConfiguration}.
 */
@TestPropertySource(properties = {"memory.enabled=true", "memory.purge-interval=1s"})
@Testcontainers(disabledWithoutDocker = true)
@ImportTestcontainers(PostgresContainers.class)
@Import(RedisPropertiesConfiguration.class)
public abstract class AbstractMemorySystemTest extends AbstractSystemTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Value("${ledger.change-stream.key}")
    protected String changeStreamKey;

    @BeforeEach
    void publishKeySet() {
        LedgerJwksStubs.stubKeySet();
    }

    @AfterEach
    void truncateIncomingMessages() {
        jdbcTemplate.update("DELETE FROM stream_entry_failure");
        IncomingMessageRowUtils.deleteAll(jdbcTemplate);
    }
}
