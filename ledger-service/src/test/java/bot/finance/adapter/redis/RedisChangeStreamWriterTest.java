package bot.finance.adapter.redis;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.adapter.cdc.CdcProperties;
import bot.finance.common.containers.RedisContainers;
import bot.finance.common.fixtures.CdcConfigurations;
import io.lettuce.core.Range;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Wires only {@link RedisChangeStreamWriter} against the containerized Redis in {@link RedisContainers}, and
 * against a closed port for the outage scenario; nothing is mocked. Entries are read back with the writer's own
 * connection factory's counterpart - a plain Lettuce client against the raw stream, rather than through the
 * writer itself, since the writer exposes no read path.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisChangeStreamWriterTest {

    @Nested
    @DisplayName("writing a change event")
    class Write {

        @Test
        @DisplayName("when a payload with an enrichment block is written - then one entry carries both fields verbatim")
        void whenPayloadWithEnrichmentIsWritten_thenOneEntryCarriesBothFieldsVerbatim() {
            String streamKey = uniqueStreamKey("happy-path");
            LettuceConnectionFactory factory = RedisContainers.connectionFactory();
            try {
                RedisChangeStreamWriter writer =
                        new RedisChangeStreamWriter(RedisContainers.template(factory), properties(streamKey, 1000));
                String payload = "{\"op\":\"c\",\"after\":{\"id\":1}}";
                String enrichment = "{\"category\":\"Groceries\"}";

                boolean written = writer.write(payload, Optional.of(enrichment));

                assertThat(written).isTrue();
                List<StreamMessage<String, String>> entries = readEntries(streamKey);
                assertThat(entries).hasSize(1);
                assertThat(entries.get(0).getBody())
                        .containsEntry("payload", payload)
                        .containsEntry("enrichment", enrichment);
            } finally {
                factory.destroy();
            }
        }

        @Test
        @DisplayName(
                "when the stream is already at its cap - then the newest survive, the oldest are gone, near the cap")
        void whenStreamIsAtItsCapAndMoreAreWritten_thenNewestSurviveOldestAreGoneNearCap() {
            String streamKey = uniqueStreamKey("at-cap");
            long cap = 5;
            LettuceConnectionFactory factory = RedisContainers.connectionFactory();
            try {
                RedisChangeStreamWriter writer =
                        new RedisChangeStreamWriter(RedisContainers.template(factory), properties(streamKey, cap));

                int written = 1000;
                for (int i = 0; i < written; i++) {
                    writer.write("{\"op\":\"c\",\"after\":{\"marker\":\"entry-" + i + "\"}}", Optional.empty());
                }

                List<String> payloads = readEntries(streamKey).stream()
                        .map(m -> m.getBody().get("payload"))
                        .toList();
                assertThat(payloads).contains("{\"op\":\"c\",\"after\":{\"marker\":\"entry-999\"}}");
                assertThat(payloads).doesNotContain("{\"op\":\"c\",\"after\":{\"marker\":\"entry-0\"}}");
                assertThat(payloads.size()).isLessThanOrEqualTo(written / 5);
            } finally {
                factory.destroy();
            }
        }

        @Test
        @DisplayName("when Redis cannot be reached - then answers not written rather than throwing")
        void whenRedisIsUnreachable_thenAnswersNotWrittenRatherThanThrowing() {
            String streamKey = uniqueStreamKey("unreachable");
            LettuceConnectionFactory factory = RedisContainers.unreachableConnectionFactory();
            try {
                RedisChangeStreamWriter writer =
                        new RedisChangeStreamWriter(RedisContainers.template(factory), properties(streamKey, 1000));

                boolean written = writer.write("{\"op\":\"c\",\"after\":{\"id\":1}}", Optional.empty());

                assertThat(written).isFalse();
            } finally {
                factory.destroy();
            }
        }
    }

    private static String uniqueStreamKey(String scenario) {
        return "test.redis-writer." + scenario + "." + UUID.randomUUID();
    }

    private static CdcProperties properties(String streamKey, long streamMaxLength) {
        return CdcConfigurations.forStream("redis_change_stream_writer_test_slot", streamKey, streamMaxLength);
    }

    private static List<StreamMessage<String, String>> readEntries(String streamKey) {
        try (RedisClient client = RedisClient.create(RedisContainers.redisUrl())) {
            try (StatefulRedisConnection<String, String> connection = client.connect()) {
                RedisCommands<String, String> commands = connection.sync();
                return commands.xrange(streamKey, Range.create("-", "+"));
            }
        }
    }
}
