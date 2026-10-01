package bot.finance.adapter.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ExpenseEntityRepository extends CrudRepository<ExpenseEntity, Long> {

    @Query(
            """
            SELECT c.name AS category_name, p.name AS grouping_name, e.description AS description,
                   e.merchant AS merchant, e.amount_minor_units AS amount_minor_units,
                   e.currency_code AS currency_code
            FROM expense e
            JOIN category c ON e.category_id = c.id
            JOIN category p ON c.parent_id = p.id
            WHERE e.user_id = :userId AND e.incoming_message_id = :incomingMessageId AND e.status = 'PENDING'
            ORDER BY e.created_at, e.id
            """)
    List<ProposalSummaryProjection> findSummariesByMessageReference(
            @Param("userId") Long userId, @Param("incomingMessageId") String incomingMessageId);

    @Query(
            """
            WITH changed AS (
                UPDATE expense
                SET status = 'RECORDED', updated_at = :now
                WHERE user_id = :userId AND incoming_message_id = :incomingMessageId AND status = 'PENDING'
                RETURNING *
            )
            SELECT c.id AS id, c.user_id AS user_id, c.incoming_message_id AS incoming_message_id,
                   c.status AS status, c.description AS description, c.merchant AS merchant,
                   c.amount_minor_units AS amount_minor_units, c.currency_code AS currency_code,
                   c.created_at AS created_at, cat.id AS category_id, cat.name AS category_name,
                   grp.id AS grouping_id, grp.name AS grouping_name
            FROM changed c
            JOIN category cat ON c.category_id = cat.id
            LEFT JOIN category grp ON cat.parent_id = grp.id
            """)
    List<SpendingRowProjection> accept(
            @Param("userId") Long userId,
            @Param("incomingMessageId") String incomingMessageId,
            @Param("now") Instant now);

    @Query(
            """
            WITH changed AS (
                DELETE FROM expense
                WHERE user_id = :userId AND incoming_message_id = :incomingMessageId AND status = 'PENDING'
                RETURNING *
            )
            SELECT c.id AS id, c.user_id AS user_id, c.incoming_message_id AS incoming_message_id,
                   c.status AS status, c.description AS description, c.merchant AS merchant,
                   c.amount_minor_units AS amount_minor_units, c.currency_code AS currency_code,
                   c.created_at AS created_at, cat.id AS category_id, cat.name AS category_name,
                   grp.id AS grouping_id, grp.name AS grouping_name
            FROM changed c
            JOIN category cat ON c.category_id = cat.id
            LEFT JOIN category grp ON cat.parent_id = grp.id
            """)
    List<SpendingRowProjection> discard(
            @Param("userId") Long userId, @Param("incomingMessageId") String incomingMessageId);

    @Query(
            """
            WITH changed AS (
                UPDATE expense
                SET status = 'RECORDED', updated_at = :now
                WHERE user_id = :userId AND id IN (:ids) AND status = 'PENDING'
                RETURNING *
            )
            SELECT c.id AS id, c.user_id AS user_id, c.incoming_message_id AS incoming_message_id,
                   c.status AS status, c.description AS description, c.merchant AS merchant,
                   c.amount_minor_units AS amount_minor_units, c.currency_code AS currency_code,
                   c.created_at AS created_at, cat.id AS category_id, cat.name AS category_name,
                   grp.id AS grouping_id, grp.name AS grouping_name
            FROM changed c
            JOIN category cat ON c.category_id = cat.id
            LEFT JOIN category grp ON cat.parent_id = grp.id
            """)
    List<SpendingRowProjection> acceptByIds(
            @Param("userId") Long userId, @Param("ids") List<Long> ids, @Param("now") Instant now);

    @Query(
            """
            SELECT DISTINCT incoming_message_id
            FROM expense
            WHERE user_id = :userId AND incoming_message_id IN (:incomingMessageIds) AND status = 'PENDING'
            """)
    List<String> findWithPendingProposals(
            @Param("userId") Long userId, @Param("incomingMessageIds") Collection<String> incomingMessageIds);

    @Query(
            """
            SELECT count(*)
            FROM expense
            WHERE user_id = :userId AND incoming_message_id = :incomingMessageId AND status = 'RECORDED'
            """)
    int countByMessageReference(@Param("userId") Long userId, @Param("incomingMessageId") String incomingMessageId);

    @Query(
            """
            SELECT currency_code, sum(amount_minor_units) AS total_minor_units, count(*) AS expense_count
            FROM expense
            WHERE user_id = :userId AND status = 'RECORDED'
              AND created_at >= :from AND created_at < :toExclusive
            GROUP BY currency_code
            ORDER BY currency_code
            """)
    List<CurrencyTotalProjection> totalsByCurrency(
            @Param("userId") Long userId, @Param("from") Instant from, @Param("toExclusive") Instant toExclusive);

    @Query(
            """
            SELECT status, id, category_id, description, merchant, amount_minor_units, currency_code, created_at
            FROM expense
            WHERE user_id = :userId
              AND (CAST(:status AS VARCHAR) IS NULL OR status = :status)
              AND (CAST(:categoryId AS BIGINT) IS NULL OR category_id = :categoryId)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR created_at >= :from)
              AND (CAST(:toExclusive AS TIMESTAMPTZ) IS NULL OR created_at < :toExclusive)
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
            SELECT count(*)
            FROM expense
            WHERE user_id = :userId
              AND (CAST(:status AS VARCHAR) IS NULL OR status = :status)
              AND (CAST(:categoryId AS BIGINT) IS NULL OR category_id = :categoryId)
              AND (CAST(:from AS TIMESTAMPTZ) IS NULL OR created_at >= :from)
              AND (CAST(:toExclusive AS TIMESTAMPTZ) IS NULL OR created_at < :toExclusive)
            """)
    long countMatching(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("categoryId") Long categoryId,
            @Param("from") Instant from,
            @Param("toExclusive") Instant toExclusive);

    @Query(
            """
            WITH changed AS (
                UPDATE expense
                SET category_id = :categoryId, updated_at = :now
                WHERE id = :id AND user_id = :userId
                RETURNING *
            )
            SELECT c.id AS id, c.user_id AS user_id, c.incoming_message_id AS incoming_message_id,
                   c.status AS status, c.description AS description, c.merchant AS merchant,
                   c.amount_minor_units AS amount_minor_units, c.currency_code AS currency_code,
                   c.created_at AS created_at, cat.id AS category_id, cat.name AS category_name,
                   grp.id AS grouping_id, grp.name AS grouping_name
            FROM changed c
            JOIN category cat ON c.category_id = cat.id
            LEFT JOIN category grp ON cat.parent_id = grp.id
            """)
    Optional<SpendingRowProjection> refile(
            @Param("userId") Long userId,
            @Param("id") Long id,
            @Param("categoryId") Long categoryId,
            @Param("now") Instant now);

    @Query(
            """
            SELECT e.id AS id, e.user_id AS user_id, e.incoming_message_id AS incoming_message_id,
                   e.status AS status, e.description AS description, e.merchant AS merchant,
                   e.amount_minor_units AS amount_minor_units, e.currency_code AS currency_code,
                   e.created_at AS created_at, cat.id AS category_id, cat.name AS category_name,
                   grp.id AS grouping_id, grp.name AS grouping_name
            FROM expense e
            JOIN category cat ON e.category_id = cat.id
            LEFT JOIN category grp ON cat.parent_id = grp.id
            WHERE e.id = :id
            """)
    Optional<SpendingRowProjection> findEventRow(@Param("id") Long id);
}
