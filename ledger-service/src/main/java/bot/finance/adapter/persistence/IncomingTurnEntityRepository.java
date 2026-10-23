package bot.finance.adapter.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface IncomingTurnEntityRepository extends CrudRepository<IncomingTurnEntity, Long> {

    @Modifying
    @Query(
            """
            INSERT INTO incoming_turn (user_id, incoming_message_id, text, conversation_id, inbound_message_id, kept_at)
            VALUES (:userId, :reference, :text, :conversationId, :inboundMessageId, :keptAt)
            ON CONFLICT (incoming_message_id) DO NOTHING
            """)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("reference") String reference,
            @Param("text") String text,
            @Param("conversationId") String conversationId,
            @Param("inboundMessageId") String inboundMessageId,
            @Param("keptAt") Instant keptAt);

    @Query(
            """
            SELECT * FROM incoming_turn
            WHERE handed_over_at IS NULL AND kept_at >= :keptSince
            ORDER BY id
            LIMIT 1
            """)
    Optional<IncomingTurnEntity> findOldestQueuedKeptSince(@Param("keptSince") Instant keptSince);

    @Modifying
    @Query(
            """
            UPDATE incoming_turn
            SET last_attempt_at = :now
            WHERE id = :id AND handed_over_at IS NULL
            """)
    int recordAttempt(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query(
            """
            UPDATE incoming_turn
            SET handed_over_at = :now
            WHERE id = :id AND handed_over_at IS NULL
            """)
    int markHandedOver(@Param("id") Long id, @Param("now") Instant now);

    @Query(
            """
            SELECT * FROM incoming_turn
            WHERE user_id = :userId AND incoming_message_id = :reference
            """)
    Optional<IncomingTurnEntity> findForUser(@Param("userId") Long userId, @Param("reference") String reference);

    @Modifying
    @Query("DELETE FROM incoming_turn WHERE id = :id")
    int deleteTurn(@Param("id") Long id);

    @Query(
            """
            SELECT * FROM incoming_turn
            WHERE handed_over_at IS NOT NULL AND handed_over_at < :cutoff
            ORDER BY id
            """)
    List<IncomingTurnEntity> findHandedOverBefore(@Param("cutoff") Instant cutoff);

    @Query(
            """
            SELECT * FROM incoming_turn
            WHERE handed_over_at IS NULL
              AND kept_at < :keptBefore
              AND COALESCE(last_attempt_at, kept_at) < :attemptBefore
            ORDER BY id
            """)
    List<IncomingTurnEntity> findQueuedForSweep(
            @Param("keptBefore") Instant keptBefore, @Param("attemptBefore") Instant attemptBefore);

    @Query(
            """
            SELECT * FROM incoming_turn
            WHERE handed_over_at IS NULL
            ORDER BY kept_at
            LIMIT 1
            """)
    Optional<IncomingTurnEntity> findOldestQueued();
}
