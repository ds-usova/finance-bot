package bot.finance.adapter.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ExpenseEntityRepository extends CrudRepository<ExpenseEntity, Long> {

    @Query(
            """
            SELECT count(*) FROM expense
            WHERE user_id = :userId AND message_reference = :messageReference
            """)
    int countByMessageReference(@Param("userId") Long userId, @Param("messageReference") UUID messageReference);

    @Query(
            """
            SELECT currency_code, sum(amount_minor_units) AS total_minor_units, count(*) AS expense_count
            FROM expense
            WHERE user_id = :userId AND created_at >= :from AND created_at < :toExclusive
            GROUP BY currency_code
            ORDER BY currency_code
            """)
    List<CurrencyTotalProjection> totalsByCurrency(
            @Param("userId") Long userId, @Param("from") Instant from, @Param("toExclusive") Instant toExclusive);
}
