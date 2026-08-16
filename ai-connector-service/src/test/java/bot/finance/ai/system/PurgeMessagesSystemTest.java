package bot.finance.ai.system;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.common.boot.AbstractMemorySystemTest;
import bot.finance.ai.common.rows.IncomingMessageRowUtils;
import java.time.Duration;
import java.time.Instant;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Entered by letting {@code MemoryPurgeScheduler} fire on its own timer — {@link AbstractMemorySystemTest} sets
 * {@code memory.purge-interval} to one second, short enough to observe within a test — never by calling
 * {@code run()} directly.
 */
class PurgeMessagesSystemTest extends AbstractMemorySystemTest {

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("when the purge timer fires - then the old row is gone and the young row is still there")
        void whenPurgeTimerFires_thenOldRowIsGoneAndYoungRowIsStillThere() {
            long oldUserId = 5001L;
            String oldIncomingMessageId = "message-5001-old";
            long youngUserId = 5002L;
            String youngIncomingMessageId = "message-5002-young";
            Instant longAgo = Instant.now().minus(Duration.ofDays(400));
            Instant now = Instant.now();
            IncomingMessageRowUtils.insert(jdbcTemplate, oldUserId, oldIncomingMessageId, "old message", longAgo);
            IncomingMessageRowUtils.insert(jdbcTemplate, youngUserId, youngIncomingMessageId, "young message", now);

            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                int oldCount = IncomingMessageRowUtils.count(jdbcTemplate, oldUserId, oldIncomingMessageId);
                log.info("old row count: {}", oldCount);
                assertThat(oldCount).isZero();
            });

            assertThat(IncomingMessageRowUtils.count(jdbcTemplate, youngUserId, youngIncomingMessageId))
                    .isEqualTo(1);
        }
    }
}
