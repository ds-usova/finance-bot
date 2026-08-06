package bot.finance.adapter.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface SpendingQueryEntityRepository extends CrudRepository<SpendingQueryEntity, Long> {

    @Query(
            """
            SELECT DISTINCT ON (period_start, period_end) period_start, period_end, created_at
            FROM spending_query
            WHERE user_id = :userId AND message_reference = :messageReference
            ORDER BY period_start, period_end, created_at
            """)
    List<SpendingPeriodProjection> findPeriodsByMessageReference(
            @Param("userId") Long userId, @Param("messageReference") UUID messageReference);

    @Modifying
    @Query(
            """
            DELETE FROM spending_query
            WHERE user_id = :userId AND message_reference = :messageReference
            """)
    int discard(@Param("userId") Long userId, @Param("messageReference") UUID messageReference);
}
