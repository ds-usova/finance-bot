package bot.finance.ai.adapter.persistence;

import bot.finance.ai.common.boot.PersistenceAdapterTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

// This class exercised eight per-operation port methods (recordProposed, settleProposalDeleted,
// settleExpenseInserted, refileExpense, removeExpense, renameCategory, renameGrouping, abandonAcceptance)
// that no longer exist - the port now carries one guarded upsert instead.
@PersistenceAdapterTest
@Import(JdbcRecordedExpenseStoreAdapter.class)
class JdbcRecordedExpenseStoreAdapterTest {

    @Nested
    @DisplayName("recording a proposed expense")
    class RecordProposed {

        @Disabled("RI01: rewritten against apply(...) - a PROPOSED entry inserts one row")
        @Test
        @DisplayName("when a registered message has no row - then one PROPOSED row holds the content and identifiers")
        void whenRegisteredMessageHasNoRow_thenOnePropsedRowHoldsContentAndIdentifiers() {}

        @Disabled("RI01: the recordProposed()/settle pairing no longer exists")
        @Test
        @DisplayName("when called again for a row already ACCEPTED - then still one row, later name, still ACCEPTED")
        void whenCalledAgainForRowAlreadyAccepted_thenStillOneRowLaterNameStillAccepted() {}

        @Disabled("RI01: rewritten against apply(...) - no registered message writes nothing")
        @Test
        @DisplayName("when no message is registered under the row's identity - then no row is written")
        void whenNoMessageRegisteredUnderIdentity_thenNoRowWritten() {}
    }

    @Nested
    @DisplayName("settling a proposal delete")
    class SettleProposalDeleted {

        @Disabled("RI01: the provisional DISCARDED-pending state no longer exists")
        @Test
        @DisplayName("when a PROPOSED row has no other row - then it becomes DISCARDED remembering the transaction")
        void whenProposedRowHasNoOtherRow_thenBecomesDiscardedRememberingTransaction() {}

        @Disabled("RI01: the pairing-by-content match no longer exists")
        @Test
        @DisplayName(
                "when an unpaired ACCEPTED row of the transaction matches - then one row remains ACCEPTED with both ids")
        void whenUnpairedAcceptedRowOfTransactionMatches_thenOneRowRemainsAcceptedWithBothIds() {}

        @Disabled("RI01: the pairing-by-content match no longer exists")
        @Test
        @DisplayName("when two unpaired ACCEPTED rows match - then the lower expense id takes the proposal id")
        void whenTwoUnpairedAcceptedRowsMatch_thenLowerExpenseIdTakesProposalId() {}

        @Disabled("RI01: the pairing-by-content match no longer exists")
        @Test
        @DisplayName("when called again for a row already ACCEPTED with both ids - then the row is unchanged")
        void whenCalledAgainForRowAlreadyAcceptedWithBothIds_thenRowUnchanged() {}

        @Disabled("RI01: rewritten against apply(...)")
        @Test
        @DisplayName("when no message is registered under the row's identity - then nothing changes")
        void whenNoMessageRegisteredForProposal_thenNothingChanges() {}
    }

    @Nested
    @DisplayName("settling an expense insert")
    class SettleExpenseInserted {

        @Disabled("RI01: the pairing-by-content match no longer exists")
        @Test
        @DisplayName(
                "when a DISCARDED row of the transaction matches - then it becomes ACCEPTED holding the expense id")
        void whenDiscardedRowOfTransactionMatches_thenBecomesAcceptedHoldingExpenseId() {}

        @Disabled("RI01: the pairing-by-content match no longer exists")
        @Test
        @DisplayName("when called twice for two equal-content DISCARDED rows - then the lower proposal id pairs first")
        void whenCalledTwiceForTwoEqualContentDiscardedRows_thenLowerProposalIdPairsFirst() {}

        @Disabled("RI01: the pairing-by-content match no longer exists")
        @Test
        @DisplayName("when three DISCARDED rows differ in content - then each pairs with its matching content")
        void whenThreeDiscardedRowsDifferInContent_thenEachPairsWithMatchingContent() {}

        @Disabled("RI01: the pairing-by-content match no longer exists")
        @Test
        @DisplayName("when the only DISCARDED match is of another transaction - then a lone ACCEPTED row is written")
        void whenOnlyDiscardedMatchOfAnotherTransaction_thenLoneAcceptedRowWritten() {}

        @Disabled("RI01: the pairing-by-content match no longer exists")
        @Test
        @DisplayName("when the only DISCARDED match is of another message - then a lone ACCEPTED row is written")
        void whenOnlyDiscardedMatchOfAnotherMessage_thenLoneAcceptedRowWritten() {}

        @Disabled("RI01: rewritten against apply(...) redelivery")
        @Test
        @DisplayName("when called again for a row already keyed by the expense id - then still one row, later name")
        void whenCalledAgainForRowAlreadyKeyedByExpenseId_thenStillOneRowLaterName() {}
    }

    @Nested
    @DisplayName("settling an acceptance's two halves concurrently")
    class SettleConcurrently {

        @Disabled("RI01: rewritten against two threads applying a refile and an acceptance of one expenseId")
        @Test
        @DisplayName("when both settle calls run at once, repeatedly - then every run ends with one ACCEPTED row "
                + "holding both ids")
        void whenBothSettleCallsRunAtOnceRepeatedly_thenEveryRunEndsWithOneAcceptedRowHoldingBothIds() {}
    }

    @Nested
    @DisplayName("refiling and removing a recorded expense")
    class RefileAndRemoveExpense {

        @Disabled("RI01: refileExpense() is gone - a refile is the same apply(...) upsert")
        @Test
        @DisplayName(
                "when refileExpense is called for an ACCEPTED row - then it holds the new category, staying ACCEPTED")
        void whenRefileExpenseCalledForAcceptedRow_thenHoldsNewCategoryStayingAccepted() {}

        @Disabled("RI01: removeExpense() is gone - an expense removal has no event yet (design F14)")
        @Test
        @DisplayName("when removeExpense is called with an ACCEPTED row's expense id - then it is gone, message stays")
        void whenRemoveExpenseCalledWithAcceptedRowExpenseId_thenGoneMessageStays() {}

        @Disabled("RI01: refileExpense()/removeExpense() are gone")
        @Test
        @DisplayName("when no row exists under the expense id - then refileExpense and removeExpense throw nothing")
        void whenNoRowUnderExpenseId_thenRefileAndRemoveThrowNothing() {}
    }

    @Nested
    @DisplayName("renaming a category or a grouping")
    class RenameCategoryAndGrouping {

        @Disabled("RI01: renameCategory()/renameGrouping() are gone - no rename event exists yet (design D1)")
        @Test
        @DisplayName("when renameCategory is called for one category - then only its rows hold the new name")
        void whenRenameCategoryCalledForOneCategory_thenOnlyItsRowsHoldNewName() {}

        @Disabled("RI01: renameCategory()/renameGrouping() are gone - no rename event exists yet (design D1)")
        @Test
        @DisplayName("when renameGrouping is called for one person - then only that person's rows are renamed")
        void whenRenameGroupingCalledForOnePerson_thenOnlyThatPersonRowsRenamed() {}

        @Disabled("RI01: renameCategory()/renameGrouping() are gone")
        @Test
        @DisplayName("when no row matches the category or the grouping - then both calls throw nothing")
        void whenNoRowMatchesCategoryOrGrouping_thenBothCallsThrowNothing() {}
    }

    @Nested
    @DisplayName("abandoning an acceptance")
    class AbandonAcceptance {

        @Disabled("RI01: abandonAcceptance() is gone - dropping an entry leaves no provisional state (design F9)")
        @Test
        @DisplayName(
                "when abandonAcceptance is called for the message's transaction - then only its row becomes UNKNOWN")
        void whenAbandonAcceptanceCalledForTransaction_thenOnlyItsRowBecomesUnknown() {}

        @Disabled("RI01: abandonAcceptance() is gone")
        @Test
        @DisplayName("when no row of the message matches the transaction - then abandonAcceptance throws nothing")
        void whenNoRowOfMessageMatchesTransaction_thenAbandonAcceptanceThrowsNothing() {}
    }

    // The scenarios below need a store that fails in a way the healthy containerized Postgres cannot be
    // made to. Each constructs its own adapter over a Mockito mock and calls the adapter's own public
    // methods directly - it is still the adapter under test, just not wired against the real database.
    @Nested
    @DisplayName("against a mocked repository, not the containerized database")
    class WithAMockedRepository {

        @Disabled("RI01: rewritten against apply(...) and upsertApplied(...)")
        @Test
        @DisplayName("when the repository throws a resource-failure or transient exception - then throws "
                + "MessageStoreUnavailableException")
        void whenRepositoryThrowsResourceFailureOrTransientException_thenThrowsMessageStoreUnavailableException() {}

        @Disabled("RI01: rewritten against apply(...) and upsertApplied(...)")
        @Test
        @DisplayName(
                "when the repository throws another DataAccessException - then throws MessageStoreFailedException, "
                        + "not its subtype")
        void whenRepositoryThrowsAnotherDataAccessException_thenThrowsMessageStoreFailedExceptionNotSubtype() {}
    }
}
