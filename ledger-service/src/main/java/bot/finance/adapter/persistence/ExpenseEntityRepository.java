package bot.finance.adapter.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ExpenseEntityRepository extends CrudRepository<ExpenseEntity, Long> {

    // answers the caller's entries under that message, oldest first, with their category and grouping names —
    // TODO: narrow this to the caller's PENDING entries alone
    @Query(
            """
            SELECT c.name AS category_name, p.name AS grouping_name, e.description AS description,
                   e.merchant AS merchant, e.amount_minor_units AS amount_minor_units,
                   e.currency_code AS currency_code
            FROM expense e
            JOIN category c ON e.category_id = c.id
            JOIN category p ON c.parent_id = p.id
            WHERE e.user_id = :userId AND e.incoming_message_id = :incomingMessageId
            ORDER BY e.created_at, e.id
            """)
    List<ProposalSummaryProjection> findSummariesByMessageReference(
            @Param("userId") Long userId, @Param("incomingMessageId") String incomingMessageId);

    // updates the caller's PENDING entries under that message to RECORDED, answering how many rows matched —
    // TODO: narrow the WHERE to PENDING and set status = 'RECORDED' in the assignment
    @Modifying
    @Query(
            """
            UPDATE expense
            SET updated_at = :now
            WHERE user_id = :userId AND incoming_message_id = :incomingMessageId
            """)
    int accept(
            @Param("userId") Long userId,
            @Param("incomingMessageId") String incomingMessageId,
            @Param("now") Instant now);

    // removes the caller's PENDING entries under that message, answering how many rows matched —
    // TODO: narrow this to the caller's PENDING entries alone
    @Modifying
    @Query(
            """
            DELETE FROM expense
            WHERE user_id = :userId AND incoming_message_id = :incomingMessageId
            """)
    int discard(@Param("userId") Long userId, @Param("incomingMessageId") String incomingMessageId);

    // updates the caller's PENDING entries named by id to RECORDED, answering each row's message id —
    // TODO: narrow the WHERE to PENDING and set status = 'RECORDED' in the assignment
    @Query(
            """
            UPDATE expense
            SET updated_at = :now
            WHERE user_id = :userId AND id IN (:ids)
            RETURNING incoming_message_id
            """)
    List<String> acceptByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids, @Param("now") Instant now);

    // answers the messages that still have a PENDING entry among those given —
    // TODO: narrow this to the caller's PENDING entries alone
    @Query(
            """
            SELECT DISTINCT incoming_message_id
            FROM expense
            WHERE user_id = :userId AND incoming_message_id IN (:incomingMessageIds)
            """)
    List<String> findWithPendingProposals(
            @Param("userId") Long userId, @Param("incomingMessageIds") Collection<String> incomingMessageIds);

    // TODO: narrow this to RECORDED entries alone, folding it and the totals/page/count queries below into one
    @Query(
            """
            SELECT count(*) FROM expense
            WHERE user_id = :userId AND incoming_message_id = :incomingMessageId
            """)
    int countByMessageReference(@Param("userId") Long userId, @Param("incomingMessageId") String incomingMessageId);

    // TODO: narrow this to RECORDED entries alone
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

    // TODO: replace the UNION ALL below with a single read over expense's own status column
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

    // TODO: replace the two-part sum below with a single count over expense's own status column
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

    // TODO: add the status predicate the widened refile(...) now carries
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
            @Param("status") String status,
            @Param("now") Instant now);
}
