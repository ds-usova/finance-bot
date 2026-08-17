package bot.finance.ai.adapter.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface RecordedExpenseEntityRepository extends CrudRepository<RecordedExpenseEntity, Long> {

    Optional<RecordedExpenseEntity> findByProposalId(long proposalId);

    Optional<RecordedExpenseEntity> findByExpenseId(long expenseId);

    @Query(
            """
            SELECT * FROM recorded_expense
            WHERE message_id IN (:messageIds) AND status IN ('ACCEPTED', 'DISCARDED')
            ORDER BY message_id, id
            """)
    List<RecordedExpenseEntity> findDecidedByMessageIds(@Param("messageIds") List<Long> messageIds);

    @Modifying
    @Query(
            """
            INSERT INTO recorded_expense (message_id, user_id, proposal_id, description, merchant,
                                           amount_minor_units, currency_code, category_id, category_name,
                                           grouping_name, status, updated_at)
            SELECT im.id, im.user_id, :proposalId, :description, :merchant, :amountMinorUnits,
                   :currencyCode, :categoryId, :categoryName, :groupingName, 'PROPOSED', :now
            FROM incoming_message im
            WHERE im.user_id = :userId AND im.incoming_message_id = :incomingMessageId
            ON CONFLICT (proposal_id) DO UPDATE
            SET category_id = EXCLUDED.category_id, category_name = EXCLUDED.category_name,
                grouping_name = EXCLUDED.grouping_name, updated_at = EXCLUDED.updated_at
            """)
    int upsertProposed(
            @Param("userId") long userId,
            @Param("incomingMessageId") String incomingMessageId,
            @Param("proposalId") long proposalId,
            @Param("description") String description,
            @Param("merchant") String merchant,
            @Param("amountMinorUnits") long amountMinorUnits,
            @Param("currencyCode") String currencyCode,
            @Param("categoryId") long categoryId,
            @Param("categoryName") String categoryName,
            @Param("groupingName") String groupingName,
            @Param("now") Instant now);

    @Modifying
    @Query(
            """
            UPDATE recorded_expense
            SET category_id = :categoryId, category_name = :categoryName, grouping_name = :groupingName,
                updated_at = :now
            WHERE expense_id = :expenseId
            """)
    int updateFiling(
            @Param("expenseId") long expenseId,
            @Param("categoryId") long categoryId,
            @Param("categoryName") String categoryName,
            @Param("groupingName") String groupingName,
            @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM recorded_expense WHERE expense_id = :expenseId")
    int deleteByExpenseId(@Param("expenseId") long expenseId);

    @Modifying
    @Query("UPDATE recorded_expense SET category_name = :name, updated_at = :now WHERE category_id = :categoryId")
    int renameCategory(@Param("categoryId") long categoryId, @Param("name") String name, @Param("now") Instant now);

    @Modifying
    @Query(
            """
            UPDATE recorded_expense
            SET grouping_name = :to, updated_at = :now
            WHERE user_id = :userId AND grouping_name = :from
            """)
    int renameGrouping(
            @Param("userId") long userId,
            @Param("from") String from,
            @Param("to") String to,
            @Param("now") Instant now);

    @Modifying
    @Query(
            """
            UPDATE recorded_expense
            SET status = 'UNKNOWN', updated_at = :now
            WHERE status = 'DISCARDED' AND moved_in_tx = :transactionId
              AND message_id = (SELECT id FROM incoming_message
                                 WHERE user_id = :userId AND incoming_message_id = :incomingMessageId)
            """)
    int markUnknown(
            @Param("userId") long userId,
            @Param("incomingMessageId") String incomingMessageId,
            @Param("transactionId") String transactionId,
            @Param("now") Instant now);

    @Modifying
    @Query(
            """
            UPDATE recorded_expense
            SET status = 'DISCARDED', moved_in_tx = :transactionId, updated_at = :now
            WHERE proposal_id = :proposalId AND status = 'PROPOSED'
            """)
    int markDiscarded(
            @Param("proposalId") long proposalId,
            @Param("transactionId") String transactionId,
            @Param("now") Instant now);

    @Query(
            """
            SELECT * FROM recorded_expense
            WHERE status = 'DISCARDED' AND moved_in_tx = :transactionId AND message_id = :messageId
              AND description = :description AND merchant IS NOT DISTINCT FROM :merchant
              AND amount_minor_units = :amountMinorUnits AND currency_code = :currencyCode
              AND category_id = :categoryId
            ORDER BY proposal_id ASC
            LIMIT 1
            """)
    Optional<RecordedExpenseEntity> findLowestDiscardedMatch(
            @Param("messageId") long messageId,
            @Param("transactionId") String transactionId,
            @Param("description") String description,
            @Param("merchant") String merchant,
            @Param("amountMinorUnits") long amountMinorUnits,
            @Param("currencyCode") String currencyCode,
            @Param("categoryId") long categoryId);

    @Query(
            """
            SELECT * FROM recorded_expense
            WHERE status = 'ACCEPTED' AND proposal_id IS NULL AND moved_in_tx = :transactionId
              AND message_id = :messageId
              AND description = :description AND merchant IS NOT DISTINCT FROM :merchant
              AND amount_minor_units = :amountMinorUnits AND currency_code = :currencyCode
              AND category_id = :categoryId
            ORDER BY expense_id ASC
            LIMIT 1
            """)
    Optional<RecordedExpenseEntity> findLowestLoneAcceptedByExpense(
            @Param("messageId") long messageId,
            @Param("transactionId") String transactionId,
            @Param("description") String description,
            @Param("merchant") String merchant,
            @Param("amountMinorUnits") long amountMinorUnits,
            @Param("currencyCode") String currencyCode,
            @Param("categoryId") long categoryId);

    @Modifying
    @Query("UPDATE recorded_expense SET status = 'ACCEPTED', expense_id = :expenseId, updated_at = :now WHERE id = :id")
    int pairWithExpenseId(@Param("id") long id, @Param("expenseId") long expenseId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE recorded_expense SET proposal_id = :proposalId, updated_at = :now WHERE id = :id")
    int pairWithProposalId(@Param("id") long id, @Param("proposalId") long proposalId, @Param("now") Instant now);

    @Modifying
    @Query(
            """
            INSERT INTO recorded_expense (message_id, user_id, expense_id, description, merchant,
                                           amount_minor_units, currency_code, category_id, category_name,
                                           grouping_name, status, moved_in_tx, updated_at)
            SELECT im.id, im.user_id, :expenseId, :description, :merchant, :amountMinorUnits, :currencyCode,
                   :categoryId, :categoryName, :groupingName, 'ACCEPTED', :transactionId, :now
            FROM incoming_message im
            WHERE im.user_id = :userId AND im.incoming_message_id = :incomingMessageId
            ON CONFLICT (expense_id) DO UPDATE
            SET category_id = EXCLUDED.category_id, category_name = EXCLUDED.category_name,
                grouping_name = EXCLUDED.grouping_name, updated_at = EXCLUDED.updated_at
            """)
    int upsertLoneAccepted(
            @Param("userId") long userId,
            @Param("incomingMessageId") String incomingMessageId,
            @Param("expenseId") long expenseId,
            @Param("description") String description,
            @Param("merchant") String merchant,
            @Param("amountMinorUnits") long amountMinorUnits,
            @Param("currencyCode") String currencyCode,
            @Param("categoryId") long categoryId,
            @Param("categoryName") String categoryName,
            @Param("groupingName") String groupingName,
            @Param("transactionId") String transactionId,
            @Param("now") Instant now);
}
