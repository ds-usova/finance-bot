package bot.finance.ai.adapter.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface RecordedExpenseEntityRepository extends CrudRepository<RecordedExpenseEntity, Long> {

    @Query(
            """
            SELECT ranked.id, ranked.message_id, ranked.user_id, ranked.expense_id, ranked.description,
                   ranked.merchant, ranked.amount, ranked.currency_code, ranked.category_id, ranked.category_name,
                   ranked.grouping_id, ranked.grouping_name, ranked.status, ranked.applied_ms, ranked.applied_seq,
                   ranked.updated_at
            FROM (
                SELECT e.*, ROW_NUMBER() OVER (PARTITION BY message_id ORDER BY id) AS rn
                FROM recorded_expense e
                WHERE e.message_id IN (:messageIds) AND e.status IN ('ACCEPTED', 'DISCARDED')) ranked
            WHERE ranked.rn <= :exampleLines
            ORDER BY ranked.message_id, ranked.id
            """)
    List<RecordedExpenseEntity> findDecidedByMessageIds(
            @Param("messageIds") List<Long> messageIds, @Param("exampleLines") int exampleLines);

    // Intent: insert the row joined to incoming_message on (userId, incomingMessageId); on conflict on
    // expense_id, overwrite every content column, the status and the position, only where the stored
    // (applied_ms, applied_seq) is less than the event's.
    @Modifying
    @Query("UPDATE recorded_expense SET updated_at = updated_at WHERE 1 = 0")
    int upsertApplied(
            @Param("userId") long userId,
            @Param("incomingMessageId") String incomingMessageId,
            @Param("expenseId") long expenseId,
            @Param("description") String description,
            @Param("merchant") String merchant,
            @Param("amount") String amount,
            @Param("currencyCode") String currencyCode,
            @Param("categoryId") long categoryId,
            @Param("categoryName") String categoryName,
            @Param("groupingId") Long groupingId,
            @Param("groupingName") String groupingName,
            @Param("status") String status,
            @Param("appliedMs") long appliedMs,
            @Param("appliedSeq") long appliedSeq,
            @Param("now") Instant now);
}
