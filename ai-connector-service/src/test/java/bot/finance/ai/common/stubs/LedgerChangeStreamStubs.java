package bot.finance.ai.common.stubs;

import bot.finance.ai.common.containers.RedisContainers;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Static helpers over {@link RedisContainers}, standing in for the ledger's own writes to {@code ledger.cdc} and
 * for a second consumer instance in the group.
 */
public final class LedgerChangeStreamStubs {

    private static final StringRedisTemplate TEMPLATE = RedisContainers.template(RedisContainers.connectionFactory());

    private LedgerChangeStreamStubs() {}

    /** XADDs the given body onto the stream, answering the entry id Redis generated. */
    public static String publish(String key, Map<String, String> body) {
        return TEMPLATE.opsForStream().add(key, body).getValue();
    }

    /** The group's pending-entry count on the stream, via XPENDING. */
    public static long pending(String key, String group) {
        return TEMPLATE.opsForStream().pending(key, group).getTotalPendingMessages();
    }

    /** Reads new entries under another consumer name within the group, via XREADGROUP, without acknowledging. */
    public static void readAsOther(String key, String group, String consumer) {
        TEMPLATE.opsForStream()
                .read(Consumer.from(group, consumer), StreamOffset.create(key, ReadOffset.lastConsumed()));
    }

    /**
     * Acknowledges every one of the group's pending entries and trims the stream to empty, leaving the stream key
     * and its consumer group - and the group's cursor - in place. Deleting the key instead would destroy the
     * group along with it.
     */
    public static void drain(String key, String group) {
        PendingMessages pendingMessages =
                TEMPLATE.opsForStream().pending(key, group, Range.unbounded(), Long.MAX_VALUE);
        List<RecordId> pendingIds =
                pendingMessages.stream().map(PendingMessage::getId).toList();
        if (!pendingIds.isEmpty()) {
            TEMPLATE.opsForStream().acknowledge(key, group, pendingIds.toArray(RecordId[]::new));
        }
        TEMPLATE.opsForStream().trim(key, 0, false);
    }

    /** Whether the named consumer group exists on the stream, via XINFO GROUPS. */
    public static boolean groupExists(String key, String group) {
        try {
            return TEMPLATE.opsForStream().groups(key).stream().anyMatch(info -> group.equals(info.groupName()));
        } catch (DataAccessException e) {
            return false;
        }
    }
}
