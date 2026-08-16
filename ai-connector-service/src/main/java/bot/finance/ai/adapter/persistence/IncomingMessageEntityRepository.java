package bot.finance.ai.adapter.persistence;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface IncomingMessageEntityRepository extends CrudRepository<IncomingMessageEntity, Long> {

    @Query(
            "SELECT id FROM incoming_message WHERE user_id = :userId AND incoming_message_id = :incomingMessageId FOR UPDATE")
    Optional<Long> lockId(@Param("userId") long userId, @Param("incomingMessageId") String incomingMessageId);

    @Modifying
    @Query(
            """
            INSERT INTO incoming_message (user_id, incoming_message_id, text, received_at)
            VALUES (:userId, :incomingMessageId, :text, now())
            ON CONFLICT (user_id, incoming_message_id) DO NOTHING
            """)
    int insertIgnoringConflict(
            @Param("userId") long userId,
            @Param("incomingMessageId") String incomingMessageId,
            @Param("text") String text);

    @Modifying
    @Query(
            """
            DELETE FROM incoming_message
            WHERE id IN (
                SELECT id FROM incoming_message
                WHERE received_at < :cut
                ORDER BY received_at
                LIMIT :batch
                FOR UPDATE SKIP LOCKED)
            """)
    int deleteReceivedBefore(@Param("cut") Instant cut, @Param("batch") int batch);
}
