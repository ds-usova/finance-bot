package bot.finance.adapter.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface WrittenMessageEntityRepository extends CrudRepository<WrittenMessageEntity, Long> {

    Optional<WrittenMessageEntity> findByIdAndUserId(Long id, Long userId);

    @Query(
            """
            UPDATE written_message
            SET status = 'READING', updated_at = :now
            WHERE id = (
                SELECT id FROM written_message
                WHERE status = 'WAITING'
                ORDER BY id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            RETURNING *
            """)
    Optional<WrittenMessageEntity> claimOldestWaiting(@Param("now") Instant now);

    @Modifying
    @Query(
            """
            UPDATE written_message SET updated_at = :now WHERE id = :id AND status = 'READING'
            """)
    int stampHandOver(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query(
            """
            UPDATE written_message
            SET status = 'DONE', outcome = :outcome, updated_at = :now
            WHERE id = :id AND status = 'READING'
            """)
    int settle(@Param("id") Long id, @Param("outcome") String outcome, @Param("now") Instant now);

    @Query(
            """
            SELECT * FROM written_message
            WHERE user_id = :userId AND status IN ('WAITING', 'READING')
            ORDER BY id
            """)
    List<WrittenMessageEntity> findUnsettledForUser(@Param("userId") Long userId);

    @Query(
            """
            SELECT * FROM written_message
            WHERE status = 'READING' AND updated_at < :cutoff
            ORDER BY id
            """)
    List<WrittenMessageEntity> findReadingUpdatedBefore(@Param("cutoff") Instant cutoff);
}
