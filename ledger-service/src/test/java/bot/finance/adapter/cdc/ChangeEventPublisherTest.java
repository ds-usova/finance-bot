package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.adapter.redis.RedisChangeStreamWriter;
import io.debezium.engine.ChangeEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChangeEventPublisherTest {

    private RedisChangeStreamWriter redisChangeStreamWriter;
    private ChangeStreamMeters meters;
    private ChangeEventPublisher publisher;

    @BeforeEach
    void setUp() {
        redisChangeStreamWriter = mock(RedisChangeStreamWriter.class);
        meters = mock(ChangeStreamMeters.class);
        publisher = new ChangeEventPublisher(redisChangeStreamWriter, meters);
    }

    private static ChangeEvent<String, String> changeEvent(String value) {
        @SuppressWarnings("unchecked")
        ChangeEvent<String, String> event = mock(ChangeEvent.class);
        when(event.value()).thenReturn(value);
        return event;
    }

    /** {@code payload} is the outbox row's raw JSON text; it is escaped here for embedding as {@code after.payload}. */
    private static String outboxInsertValue(String id, String type, String occurredAt, String payload) {
        String escapedPayload = payload.replace("\"", "\\\"");
        return """
                {
                  "before": null,
                  "after": {
                    "id": "%s",
                    "type": "%s",
                    "occurred_at": "%s",
                    "payload": "%s"
                  },
                  "source": { "table": "outbox" },
                  "op": "c",
                  "ts_ms": 1700000000000
                }
                """
                .formatted(id, type, occurredAt, escapedPayload);
    }

    @Nested
    @DisplayName("publishing a captured change event")
    class Publish {

        @Test
        @DisplayName("when an outbox insert is published - then the writer is handed the four columns and "
                + "publish answers published")
        void whenOutboxInsertIsPublished_thenWriterIsHandedTheFourColumnsAndPublishAnswersPublished() {
            String id = UUID.randomUUID().toString();
            String type = "ProposalCreated";
            String occurredAt = "2026-08-18T10:15:30.123456Z";
            String payload = "{\"userId\":41,\"expenseId\":9013}";
            when(redisChangeStreamWriter.write(id, type, occurredAt, payload)).thenReturn(true);

            boolean published = publisher.publish(changeEvent(outboxInsertValue(id, type, occurredAt, payload)));

            verify(redisChangeStreamWriter).write(id, type, occurredAt, payload);
            assertThat(published).isTrue();
        }

        @Test
        @DisplayName("when the same outbox insert is published twice - then the writer sees identical values")
        void whenSameOutboxInsertIsPublishedTwice_thenWriterSeesIdenticalValues() {
            String id = UUID.randomUUID().toString();
            String type = "ProposalAccepted";
            String occurredAt = "2026-08-18T10:20:00.000001Z";
            String payload = "{\"userId\":41,\"expenseId\":9013,\"status\":\"RECORDED\"}";
            when(redisChangeStreamWriter.write(id, type, occurredAt, payload)).thenReturn(true);
            ChangeEvent<String, String> event = changeEvent(outboxInsertValue(id, type, occurredAt, payload));

            publisher.publish(event);
            publisher.publish(event);

            verify(redisChangeStreamWriter, times(2)).write(id, type, occurredAt, payload);
        }

        @Test
        @DisplayName("when the payload nests objects - then it reaches the writer unchanged")
        void whenPayloadNestsObjects_thenItReachesWriterUnchanged() {
            String id = UUID.randomUUID().toString();
            String type = "ProposalCreated";
            String occurredAt = "2026-08-18T10:25:00.000000Z";
            String payload =
                    "{\"userId\":41,\"category\":{\"id\":77,\"name\":\"Coffee\"},\"grouping\":{\"id\":12,\"name\":\"Dining\"}}";
            when(redisChangeStreamWriter.write(id, type, occurredAt, payload)).thenReturn(true);

            publisher.publish(changeEvent(outboxInsertValue(id, type, occurredAt, payload)));

            verify(redisChangeStreamWriter).write(id, type, occurredAt, payload);
        }

        @Test
        @DisplayName("when the writer refuses - then publish answers not published and a publish failure is counted")
        void whenWriterRefuses_thenPublishAnswersNotPublishedAndPublishFailureCounted() {
            String id = UUID.randomUUID().toString();
            String type = "ExpenseRecorded";
            String occurredAt = "2026-08-18T10:30:00.000000Z";
            String payload = "{\"userId\":41,\"expenseId\":9014}";
            when(redisChangeStreamWriter.write(id, type, occurredAt, payload)).thenReturn(false);

            boolean published = publisher.publish(changeEvent(outboxInsertValue(id, type, occurredAt, payload)));

            assertThat(published).isFalse();
            verify(meters).countPublishFailure();
        }

        @Test
        @DisplayName("when an event publishes - then the published counter is tagged by type and the lag gauge "
                + "is set from occurred_at")
        void whenEventPublishes_thenPublishedCounterTaggedAndLagGaugeSetFromOccurredAt() {
            String id = UUID.randomUUID().toString();
            String type = "ExpenseRefiled";
            Instant occurredAtInstant = Instant.now().minusSeconds(60);
            String occurredAt = occurredAtInstant.toString();
            String payload = "{\"userId\":41,\"expenseId\":9015}";
            when(redisChangeStreamWriter.write(id, type, occurredAt, payload)).thenReturn(true);

            publisher.publish(changeEvent(outboxInsertValue(id, type, occurredAt, payload)));

            verify(meters).countPublished(type);
            verify(meters).setEventLag(occurredAtInstant);
        }
    }
}
