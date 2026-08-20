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

    private static String uniqueKey() {
        return "ledger.cdc-" + UUID.randomUUID();
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
            String key = uniqueKey();
            LedgerChangeStreamStubs.createGroup(key, ChangeStreamProperties.GROUP);
            LedgerChangeStreamStubs.publish(key, ChangeStreamEntryFixtures.withNoPayload());
            LedgerChangeStreamStubs.publish(key, ChangeStreamEntryFixtures.withNoPayload());
            LedgerChangeStreamStubs.readAsOther(key, ChangeStreamProperties.GROUP, "counting-consumer");

            LettuceConnectionFactory connectionFactory = RedisContainers.connectionFactory();
            try {
                double count = pendingEntries(connectionFactory, key).count();

                assertThat(count).isEqualTo(2.0);
            } finally {
                connectionFactory.destroy();
            }
        }

        @Test
        @DisplayName("when every delivered entry is acknowledged - then it answers 0")
        void whenEveryDeliveredEntryIsAcknowledged_thenItAnswersZero() {
            String key = uniqueKey();
            LedgerChangeStreamStubs.createGroup(key, ChangeStreamProperties.GROUP);
            LedgerChangeStreamStubs.publish(key, ChangeStreamEntryFixtures.withNoPayload());
            LedgerChangeStreamStubs.readAsOther(key, ChangeStreamProperties.GROUP, "counting-consumer");
            LedgerChangeStreamStubs.drain(key, ChangeStreamProperties.GROUP);

            LettuceConnectionFactory connectionFactory = RedisContainers.connectionFactory();
            try {
                double count = pendingEntries(connectionFactory, key).count();

                assertThat(count).isZero();
            } finally {
                connectionFactory.destroy();
            }
        }

        @Test
        @DisplayName("when the connection is cut after a positive count was seen - then nothing is thrown and "
                + "the last seen value is answered")
        void whenConnectionCutAfterPositiveCountSeen_thenNothingIsThrownAndLastSeenValueIsAnswered() {
            String key = uniqueKey();
            LedgerChangeStreamStubs.createGroup(key, ChangeStreamProperties.GROUP);
            LedgerChangeStreamStubs.publish(key, ChangeStreamEntryFixtures.withNoPayload());
            LedgerChangeStreamStubs.readAsOther(key, ChangeStreamProperties.GROUP, "counting-consumer");

            LettuceConnectionFactory connectionFactory =
                    RedisContainers.connectionFactoryFor(ToxiproxyContainers.proxiedRedisUrl());
            try {
                RedisPendingEntries entries = pendingEntries(connectionFactory, key);

                double firstCount = entries.count();
                assertThat(firstCount).isEqualTo(1.0);

                ToxiproxyContainers.REDIS_PROXY.setConnectionCut(true);
                AtomicReference<Double> secondCount = new AtomicReference<>();
                try {
                    assertThatCode(() -> secondCount.set(entries.count())).doesNotThrowAnyException();
                } finally {
                    ToxiproxyContainers.REDIS_PROXY.setConnectionCut(false);
                }

                assertThat(secondCount.get()).isEqualTo(firstCount);
            } finally {
                connectionFactory.destroy();
            }
        }

        @Test
        @DisplayName("when a fresh instance finds Redis unreachable - then nothing is thrown and NaN is answered")
        void whenFreshInstanceFindsRedisUnreachable_thenNothingIsThrownAndNaNIsAnswered() {
            LettuceConnectionFactory connectionFactory = RedisContainers.unreachableConnectionFactory();
            try {
                RedisPendingEntries entries = pendingEntries(connectionFactory, uniqueKey());

                AtomicReference<Double> result = new AtomicReference<>();
                assertThatCode(() -> result.set(entries.count())).doesNotThrowAnyException();

                assertThat(result.get()).isNaN();
            } finally {
                connectionFactory.destroy();
            }
        }
    }
}
