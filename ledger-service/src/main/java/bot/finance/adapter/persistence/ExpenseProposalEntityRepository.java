package bot.finance.adapter.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ExpenseProposalEntityRepository extends CrudRepository<ExpenseProposalEntity, Long> {

    @Query(
            """
            SELECT c.name AS category_name, p.name AS grouping_name, ep.description AS description,
                   ep.merchant AS merchant, ep.amount_minor_units AS amount_minor_units,
                   ep.currency_code AS currency_code
            FROM expense_proposal ep
            JOIN category c ON ep.category_id = c.id
            JOIN category p ON c.parent_id = p.id
            WHERE ep.user_id = :userId AND ep.incoming_message_id = :incomingMessageId
            ORDER BY ep.created_at, ep.id
            """)
    List<ProposalSummaryProjection> findSummariesByMessageReference(
            @Param("userId") Long userId, @Param("incomingMessageId") String incomingMessageId);

    @Modifying
    @Query(
            """
            WITH accepted AS (
                DELETE FROM expense_proposal
                WHERE user_id = :userId AND incoming_message_id = :incomingMessageId
                RETURNING user_id, category_id, description, merchant,
                          amount_minor_units, currency_code, incoming_message_id, created_at
            )
            INSERT INTO expense (user_id, category_id, description, merchant,
                                 amount_minor_units, currency_code, incoming_message_id, created_at, updated_at)
            SELECT user_id, category_id, description, merchant,
                   amount_minor_units, currency_code, incoming_message_id, created_at, :now
            FROM accepted
            """)
    int accept(
            @Param("userId") Long userId,
            @Param("incomingMessageId") String incomingMessageId,
            @Param("now") Instant now);

    @Modifying
    @Query(
            """
            DELETE FROM expense_proposal
            WHERE user_id = :userId AND incoming_message_id = :incomingMessageId
            """)
    int discard(@Param("userId") Long userId, @Param("incomingMessageId") String incomingMessageId);

    @Query(
            """
            WITH accepted AS (
                DELETE FROM expense_proposal
                WHERE user_id = :userId AND id IN (:ids)
                RETURNING user_id, category_id, description, merchant,
                          amount_minor_units, currency_code, incoming_message_id, created_at
            )
            INSERT INTO expense (user_id, category_id, description, merchant,
                                 amount_minor_units, currency_code, incoming_message_id, created_at, updated_at)
            SELECT user_id, category_id, description, merchant,
                   amount_minor_units, currency_code, incoming_message_id, created_at, :now
            FROM accepted
            RETURNING incoming_message_id
            """)
    List<String> acceptByIds(@Param("userId") Long userId, @Param("ids") List<Long> ids, @Param("now") Instant now);

    @Query(
            """
            SELECT DISTINCT incoming_message_id
            FROM expense_proposal
            WHERE user_id = :userId AND incoming_message_id IN (:incomingMessageIds)
            """)
    List<String> findWithPendingProposals(
            @Param("userId") Long userId, @Param("incomingMessageIds") Collection<String> incomingMessageIds);

    @Query(
            """
            UPDATE expense_proposal
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
