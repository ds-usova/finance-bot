package bot.finance.ai.adapter.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface IncomingMessageEntityRepository extends CrudRepository<IncomingMessageEntity, Long> {

    @Query(
            "SELECT id FROM incoming_message WHERE user_id = :userId AND incoming_message_id = :incomingMessageId FOR UPDATE")
    Optional<Long> lockId(@Param("userId") long userId, @Param("incomingMessageId") String incomingMessageId);

    @Query(
            """
            SELECT id, embedding::text AS embedding FROM incoming_message
            WHERE user_id = :userId AND incoming_message_id = :incomingMessageId
            """)
    Optional<MessageEmbeddingRow> findEmbeddingRowByIdentity(
            @Param("userId") long userId, @Param("incomingMessageId") String incomingMessageId);

    @Modifying
    @Query(
            """
            UPDATE incoming_message
            SET embedding = CAST(:embedding AS vector), backfill_claimed_at = NULL
            WHERE id = :id AND embedding IS NULL
            """)
    int storeEmbedding(@Param("id") long id, @Param("embedding") String embedding);

    @Query(
            """
            UPDATE incoming_message
            SET embedding_attempts = embedding_attempts + 1, backfill_claimed_at = NULL
            WHERE id = :id
            RETURNING embedding_attempts
            """)
    int countEmbeddingAttempt(@Param("id") long id);

    @Query(
            """
            SELECT im.id, 1 - (im.embedding <=> CAST(:embedding AS vector)) AS similarity
            FROM incoming_message im
            WHERE im.user_id = :userId
              AND im.id != :excludeId
              AND im.embedding IS NOT NULL
              AND 1 - (im.embedding <=> CAST(:embedding AS vector)) >= :minSimilarity
              AND im.received_at >= :cut
              AND EXISTS (
                  SELECT 1 FROM recorded_expense re
                  WHERE re.message_id = im.id AND re.status IN ('ACCEPTED', 'DISCARDED'))
            ORDER BY im.embedding <=> CAST(:embedding AS vector)
            LIMIT :limit
            """)
    List<ClosestMatchRow> findClosestMatches(
            @Param("userId") long userId,
            @Param("embedding") String embedding,
            @Param("excludeId") long excludeId,
            @Param("minSimilarity") double minSimilarity,
            @Param("cut") Instant cut,
            @Param("limit") int limit);

    @Query(
            """
            SELECT im.id FROM incoming_message im
            WHERE im.user_id = :userId
              AND im.id != :excludeId
              AND im.embedding IS NOT NULL
              AND 1 - (im.embedding <=> CAST(:embedding AS vector)) >= :minSimilarity
              AND im.received_at >= :cut
              AND im.received_at >= :recentCut
              AND EXISTS (
                  SELECT 1 FROM recorded_expense re
                  WHERE re.message_id = im.id AND re.status IN ('ACCEPTED', 'DISCARDED'))
            ORDER BY im.embedding <=> CAST(:embedding AS vector)
            LIMIT 1
            """)
    Optional<Long> findClosestRecentId(
            @Param("userId") long userId,
            @Param("embedding") String embedding,
            @Param("excludeId") long excludeId,
            @Param("minSimilarity") double minSimilarity,
            @Param("cut") Instant cut,
            @Param("recentCut") Instant recentCut);

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

    @Query(
            """
            UPDATE incoming_message
            SET backfill_claimed_at = :claimedAt
            WHERE id IN (
                SELECT id FROM incoming_message
                WHERE embedding IS NULL AND embedding_attempts < :maxAttempts
                  AND (backfill_claimed_at IS NULL OR backfill_claimed_at < :staleBefore)
                ORDER BY id
                LIMIT :batch
                FOR UPDATE SKIP LOCKED)
            RETURNING id, user_id, incoming_message_id, text, received_at
            """)
    List<IncomingMessageEntity> claimUnembedded(
            @Param("claimedAt") Instant claimedAt,
            @Param("maxAttempts") int maxAttempts,
            @Param("staleBefore") Instant staleBefore,
            @Param("batch") int batch);
}
