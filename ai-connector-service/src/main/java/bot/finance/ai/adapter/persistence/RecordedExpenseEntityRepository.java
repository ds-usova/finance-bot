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

    @Modifying
    @Query(
            """
            INSERT INTO recorded_expense (
                message_id, user_id, expense_id, description, merchant, amount, currency_code,
                category_id, category_name, grouping_id, grouping_name, status, applied_ms, applied_seq,
                updated_at)
            SELECT im.id, :userId, :expenseId, :description, :merchant, :amount, :currencyCode,
                   :categoryId, :categoryName, :groupingId, :groupingName, :status, :appliedMs, :appliedSeq, :now
            FROM incoming_message im
            WHERE im.user_id = :userId AND im.incoming_message_id = :incomingMessageId
            ON CONFLICT (expense_id) DO UPDATE SET
                description = EXCLUDED.description,
                merchant = EXCLUDED.merchant,
                amount = EXCLUDED.amount,
                currency_code = EXCLUDED.currency_code,
                category_id = EXCLUDED.category_id,
                category_name = EXCLUDED.category_name,
                grouping_id = EXCLUDED.grouping_id,
                grouping_name = EXCLUDED.grouping_name,
                status = EXCLUDED.status,
                applied_ms = EXCLUDED.applied_ms,
                applied_seq = EXCLUDED.applied_seq,
                updated_at = EXCLUDED.updated_at
            WHERE (recorded_expense.applied_ms, recorded_expense.applied_seq)
                < (EXCLUDED.applied_ms, EXCLUDED.applied_seq)
            """)
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
