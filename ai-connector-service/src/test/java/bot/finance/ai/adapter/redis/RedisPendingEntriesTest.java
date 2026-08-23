package bot.finance.ai.adapter.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import bot.finance.ai.adapter.logging.Slf4jLoggerFactory;
import bot.finance.ai.common.containers.RedisContainers;
import bot.finance.ai.common.containers.ToxiproxyContainers;
import bot.finance.ai.common.fixtures.ChangeStreamEntryFixtures;
import bot.finance.ai.common.stubs.LedgerChangeStreamStubs;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Driven directly against the containerized Redis via {@link RedisContainers}, with no Spring context of its own,
 * so the consumer's draining loop never runs beside it. Each test builds its own {@link RedisPendingEntries}
 * instance and its own stream key, so it never races {@link ChangeStreamConsumerTest}'s group.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisPendingEntriesTest {

    private static final Duration CLAIM_IDLE = Duration.ofSeconds(30);
    private static final String OTHER_CONSUMER = "counting-consumer";

    private static String uniqueKey() {
        return "ledger.cdc-" + UUID.randomUUID();
    }

    /** A stream key of its own carrying {@code entries} entries delivered to the group and left unacknowledged. */
    private static String keyWithDeliveredEntries(int entries) {
        String key = uniqueKey();
        LedgerChangeStreamStubs.createGroup(key, ChangeStreamProperties.GROUP);
        for (int i = 0; i < entries; i++) {
            LedgerChangeStreamStubs.publish(key, ChangeStreamEntryFixtures.withNoPayload());
        }
        LedgerChangeStreamStubs.readAsOther(key, ChangeStreamProperties.GROUP, OTHER_CONSUMER);
        return key;
    }

    private static void withPendingEntries(
            LettuceConnectionFactory connectionFactory, String key, Consumer<RedisPendingEntries> assertions) {
        try {
            assertions.accept(pendingEntries(connectionFactory, key));
        } finally {
            connectionFactory.destroy();
        }
    }

    private static RedisPendingEntries pendingEntries(LettuceConnectionFactory connectionFactory, String key) {
        StringRedisTemplate template = RedisContainers.template(connectionFactory);
        ChangeStreamProperties properties = new ChangeStreamProperties(key, CLAIM_IDLE);
        return new RedisPendingEntries(template, properties, new Slf4jLoggerFactory());
    }

    @Nested
    @DisplayName("count()")
    class Count {

        @Test
        @DisplayName("when entries are delivered to the group and not acknowledged - then it answers their count")
        void whenEntriesDeliveredAndNotAcknowledged_thenItAnswersTheirCount() {
            String key = keyWithDeliveredEntries(2);

            withPendingEntries(RedisContainers.connectionFactory(), key, entries -> assertThat(entries.count())
                    .isEqualTo(2.0));
        }

        @Test
        @DisplayName("when every delivered entry is acknowledged - then it answers 0")
        void whenEveryDeliveredEntryIsAcknowledged_thenItAnswersZero() {
            String key = keyWithDeliveredEntries(1);
            LedgerChangeStreamStubs.drain(key, ChangeStreamProperties.GROUP);

            withPendingEntries(RedisContainers.connectionFactory(), key, entries -> assertThat(entries.count())
                    .isZero());
        }

        @Test
        @DisplayName("when the connection is cut after a positive count was seen - then nothing is thrown and "
                + "the last seen value is answered")
        void whenConnectionCutAfterPositiveCountSeen_thenNothingIsThrownAndLastSeenValueIsAnswered() {
            String key = keyWithDeliveredEntries(1);

            withPendingEntries(
                    RedisContainers.connectionFactoryFor(ToxiproxyContainers.proxiedRedisUrl()), key, entries -> {
                        double firstCount = entries.count();
                        assertThat(firstCount).isEqualTo(1.0);

                        ToxiproxyContainers.REDIS_PROXY.setConnectionCut(true);
                        AtomicReference<Double> secondCount = new AtomicReference<>();
                        try {
                            assertThatCode(() -> secondCount.set(entries.count()))
                                    .doesNotThrowAnyException();
                        } finally {
                            ToxiproxyContainers.REDIS_PROXY.setConnectionCut(false);
                        }

                        assertThat(secondCount.get()).isEqualTo(firstCount);
                    });
        }

        @Test
        @DisplayName("when a fresh instance finds Redis unreachable - then nothing is thrown and NaN is answered")
        void whenFreshInstanceFindsRedisUnreachable_thenNothingIsThrownAndNaNIsAnswered() {
            withPendingEntries(RedisContainers.unreachableConnectionFactory(), uniqueKey(), entries -> {
                AtomicReference<Double> result = new AtomicReference<>();
                assertThatCode(() -> result.set(entries.count())).doesNotThrowAnyException();

                assertThat(result.get()).isNaN();
            });
        }
    }
}
