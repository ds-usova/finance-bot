package bot.finance.adapter.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ExpenseEntityRepository extends CrudRepository<ExpenseEntity, Long> {

    @Query(
            """
            SELECT count(*) FROM expense
            WHERE user_id = :userId AND incoming_message_id = :incomingMessageId
            """)
    int countByMessageReference(@Param("userId") Long userId, @Param("incomingMessageId") String incomingMessageId);

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

    @Query(
            """
            SELECT 'RECORDED' AS status, e.id AS id, e.category_id AS category_id, e.description AS description,
                   e.merchant AS merchant, e.amount_minor_units AS amount_minor_units,
                   e.currency_code AS currency_code, e.created_at AS created_at
            FROM expense e
            WHERE e.user_id = :userId
              AND (CAST(:status AS VARCHAR) IS NULL OR :status = 'RECORDED')
              AND (CAST(:categoryId AS BIGINT) IS NULL OR e.category_id = :categoryId)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR e.created_at >= :from)
              AND (CAST(:toExclusive AS TIMESTAMPTZ) IS NULL OR e.created_at < :toExclusive)
            UNION ALL
            SELECT 'PENDING', ep.id, ep.category_id, ep.description, ep.merchant,
                   ep.amount_minor_units, ep.currency_code, ep.created_at
            FROM expense_proposal ep
            WHERE ep.user_id = :userId
              AND (CAST(:status AS VARCHAR) IS NULL OR :status = 'PENDING')
              AND (CAST(:categoryId AS BIGINT) IS NULL OR ep.category_id = :categoryId)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR ep.created_at >= :from)
              AND (CAST(:toExclusive AS TIMESTAMPTZ) IS NULL OR ep.created_at < :toExclusive)
            ORDER BY created_at DESC, status, id DESC
            LIMIT :limit OFFSET :offset
            """)
    List<ExpenseEntryProjection> findPage(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("categoryId") Long categoryId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset);

    @Query(
            """
            SELECT (
                SELECT count(*) FROM expense e
                WHERE e.user_id = :userId
                  AND (CAST(:status AS VARCHAR) IS NULL OR :status = 'RECORDED')
                  AND (CAST(:categoryId AS BIGINT) IS NULL OR e.category_id = :categoryId)
                  AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR e.created_at >= :from)
                  AND (CAST(:toExclusive AS TIMESTAMPTZ) IS NULL OR e.created_at < :toExclusive)
            ) + (
                SELECT count(*) FROM expense_proposal ep
                WHERE ep.user_id = :userId
                  AND (CAST(:status AS VARCHAR) IS NULL OR :status = 'PENDING')
                  AND (CAST(:categoryId AS BIGINT) IS NULL OR ep.category_id = :categoryId)
                  AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR ep.created_at >= :from)
                  AND (CAST(:toExclusive AS TIMESTAMPTZ) IS NULL OR ep.created_at < :toExclusive)
            ) AS total
            """)
    long countMatching(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("categoryId") Long categoryId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);

    @Query(
            """
            UPDATE expense
            SET category_id = :categoryId, updated_at = :now
            WHERE id = :id AND user_id = :userId
            RETURNING id, category_id, description, merchant, amount_minor_units, currency_code, created_at
            """)
    Optional<RefiledEntryProjection> refile(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("categoryId") Long categoryId,
            @Param("now") Instant now);
}
