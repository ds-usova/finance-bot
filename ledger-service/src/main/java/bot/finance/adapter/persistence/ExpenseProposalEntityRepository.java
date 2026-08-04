package bot.finance.adapter.persistence;

import java.util.List;
import java.util.UUID;
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
            WHERE ep.user_id = :userId AND ep.message_reference = :messageReference
            ORDER BY ep.created_at, ep.id
            """)
    List<ProposalSummaryProjection> findSummariesByMessageReference(
            @Param("userId") Long userId, @Param("messageReference") UUID messageReference);
}
