package bot.finance.ai.common.boot;

import bot.finance.ai.common.containers.PostgresContainers;
import bot.finance.ai.common.fixtures.CallerTokens;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import bot.finance.ai.common.stubs.LedgerJwksStubs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A system test with the memory on, against the real, containerized database and a purge interval short enough
 * to observe within a test. Publishes {@link CallerTokens}' key set before each test, and truncates
 * {@code incoming_message} after it so one class's rows never leak into the next. Skips when Docker is down.
 */
@TestPropertySource(properties = {"memory.enabled=true", "memory.purge-interval=1s"})
@Testcontainers(disabledWithoutDocker = true)
@ImportTestcontainers(PostgresContainers.class)
public abstract class AbstractMemorySystemTest extends AbstractSystemTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void publishKeySet() {
        LedgerJwksStubs.stubKeySet();
    }

    @AfterEach
    void truncateIncomingMessages() {
        IncomingMessageRowUtils.deleteAll(jdbcTemplate);
    }
}
