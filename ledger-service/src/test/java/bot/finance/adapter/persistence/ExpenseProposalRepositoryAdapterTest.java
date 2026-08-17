package bot.finance.adapter.persistence;

import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.value.IncomingMessageId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

// The operations this class covered are the ones ExpenseRepositoryAdapterTest now covers against the one
// adapter; the imports of ExpenseProposal, ExpenseProposalEntity, ExpenseProposalRepositoryAdapter and
// ExpenseProposalEntityRepository below are removed since those types no longer exist, and every method body
// that used them keeps its signature with the body left to be filled back in.
@Disabled(
        "RI01: ExpenseProposalRepositoryAdapter is gone, its operations are rewritten onto ExpenseRepositoryAdapterTest")
@PersistenceAdapterTest
@Import(ExpenseRepositoryAdapter.class)
class ExpenseProposalRepositoryAdapterTest {

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Nested
    @DisplayName("creating an expense proposal")
    class Create {

        @Test
        @DisplayName(
                "when called with a proposal carrying a merchant - then the row holds what was given and carries a generated id")
        void whenCalledWithMerchant_thenRowWrittenWithGivenFieldsAndReturnedProposalCarriesGeneratedId() {
            // long userId = storedUserId("merchant-proposal-user");
            // long categoryId = storedGroupingId(userId, "Groceries");
            // ExpenseProposal proposal = ExpenseProposal.newExpenseProposal(...);
            // ExpenseProposal created = adapter.create(proposal);
            // assertThat(created.id()).isPresent();
            // ... (see git history for the full body)
        }

        @Test
        @DisplayName(
                "when the proposal carries no merchant - then the stored row and the returned proposal both carry none")
        void whenCalledWithNoMerchant_thenStoredRowAndReturnedProposalBothCarryNoMerchant() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when the proposal is stamped with nanosecond precision - then its timestamps are truncated to microseconds")
        void whenInstantCarriesNanosecondPrecision_thenTimestampsAreTruncatedToMicroseconds() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when the description is exactly 500 characters long - then the row carries the whole description")
        void whenDescriptionIsExactly500Characters_thenRowIsWrittenAndCarriesWholeDescription() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when the description is 501 characters long - then throws InvalidExpenseProposalException and writes nothing")
        void whenDescriptionIs501Characters_thenThrowsInvalidExpenseProposalExceptionAndWritesNothing() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when the merchant is exactly 255 characters long - then the row carries the whole merchant")
        void whenMerchantIsExactly255Characters_thenRowIsWrittenAndCarriesWholeMerchant() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when the merchant is 256 characters long - then throws InvalidExpenseProposalException and writes nothing")
        void whenMerchantIs256Characters_thenThrowsInvalidExpenseProposalExceptionAndWritesNothing() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when the category id names no stored category - then throws EntityNotFoundException for the category")
        void whenCategoryIdNamesNoStoredCategory_thenThrowsEntityNotFoundExceptionForCategory() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when the user id names no stored user - then throws EntityNotFoundException for the user")
        void whenUserIdNamesNoStoredUser_thenThrowsEntityNotFoundExceptionForUser() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when two users each have a proposal created - then each owns exactly its own row")
        void whenCalledForTwoDifferentUsers_thenEachOwnsExactlyItsOwnRow() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when the proposal carries a message reference - then the row's message_reference column holds its UUID")
        void whenCalledWithMessageReference_thenRowMessageReferenceColumnHoldsItsUuid() {
            // see git history for the full body
        }
    }

    @Nested
    @DisplayName("finding proposal summaries by message reference")
    class FindSummariesByMessageReference {

        @Test
        @DisplayName(
                "when three proposals were stored under one reference - then returns three fully mapped summaries, oldest first")
        void whenThreeProposalsStoredUnderSameReference_thenReturnsThreeSummariesOldestFirstWithFullMapping() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when a user's proposals were stored under two references - then only the one asked for comes back")
        void whenUserHasProposalsUnderTwoReferences_thenOnlyTheOneAskedForComesBack() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when called with one user's id and a reference value two stored users share - then only that user's proposals come back")
        void whenTwoUsersShareAReferenceValue_thenOnlyRequestedUsersProposalsComeBack() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when called with a stored user and a reference nothing was written under - then returns an empty list")
        void whenReferenceHasNoStoredProposals_thenReturnsEmptyList() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when a stored proposal's merchant column is null - then that summary's merchant is Optional.empty()")
        void whenStoredProposalsMerchantColumnIsNull_thenSummaryMerchantIsEmpty() {
            // see git history for the full body
        }
    }

    @Nested
    @DisplayName("accepting proposals under a message reference")
    class Accept {

        @Test
        @DisplayName("when two proposals share a reference and a third does not - then only those two move to expense")
        void whenTwoProposalsShareAReference_thenOnlyThoseMoveToExpense() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when the stored proposal's merchant is null - then the written expense row's merchant is null")
        void whenProposalMerchantColumnIsNull_thenWrittenExpenseRowMerchantColumnIsNull() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when nothing was stored under the reference - then returns 0 and writes no expense row")
        void whenReferenceHasNoStoredProposals_thenReturnsZeroAndWritesNoExpenseRow() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when two users share a reference value - then accepting for one leaves the other's proposal untouched")
        void whenTwoUsersShareReferenceValue_thenReturnsOneAndOnlyFirstUsersProposalMoves() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when now carries nanosecond precision - then the written updated_at is truncated to microseconds")
        void whenNowCarriesNanosecondPrecision_thenWrittenTimestampsAreTruncatedToMicroseconds() {
            // see git history for the full body
        }
    }

    @Nested
    @DisplayName("discarding proposals under a message reference")
    class Discard {

        @Test
        @DisplayName("when two proposals share a reference and a third does not - then only those two are discarded")
        void whenTwoProposalsShareAReference_thenOnlyThoseTwoAreDiscarded() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when called with a stored user and a reference nothing was written under - then returns 0")
        void whenReferenceHasNoStoredProposals_thenReturnsZero() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when two users share a reference value - then discarding for one leaves the other's row intact")
        void whenTwoUsersShareReferenceValue_thenReturnsOneAndSecondUsersRowSurvives() {
            // see git history for the full body
        }
    }

    @Nested
    @DisplayName("accepting proposals by id")
    class AcceptByIds {

        @Test
        @DisplayName(
                "when two proposals share a reported message - then both move and the answer holds that message twice")
        void whenTwoProposalsShareReportedMessage_thenBothMoveAndAnswerHoldsMessageTwice() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when a single pending proposal is accepted - then it moves and keeps the message it was reported on")
        void whenOnePendingProposalAcceptedAlone_thenItMovesAndKeepsReportedMessage() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when two proposals reported on two different messages are accepted together - then the answer holds each message once")
        void whenTwoProposalsReportedOnTwoDifferentMessages_thenAnswerHoldsEachMessageOnce() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when the same ids were accepted a moment earlier - then the answer is empty and no second expense row is stored")
        void whenSameIdsAcceptedAMomentEarlier_thenAnswerIsEmptyAndNoSecondExpenseRowStored() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when an id names another person's pending proposal - then the answer is empty and that person's row still stands")
        void whenIdNamesAnotherPersonsProposal_thenAnswerIsEmptyAndOtherPersonsRowStillStands() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when an id names the caller's own expense rather than a proposal - then nothing is accepted or written")
        void whenIdNamesCallersOwnExpenseRatherThanProposal_thenNothingIsAcceptedOrWritten() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when accepting a proposal made days ago - then the expense keeps that day and is updated now")
        void whenAcceptingAProposalMadeDaysAgo_thenExpenseKeepsThatDayAndIsUpdatedNow() {
            // see git history for the full body
        }
    }

    @Nested
    @DisplayName("finding which messages still hold a pending proposal")
    class FindWithPendingProposals {

        @Test
        @DisplayName("when only one of two messages still holds a pending proposal - then only that one is answered")
        void whenOnlyOneOfTwoMessagesStillHoldsPendingProposal_thenOnlyThatOneIsAnswered() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when a message's only remaining proposal belongs to another person - then it is not answered for the caller")
        void whenMessagesOnlyRemainingProposalBelongsToAnotherPerson_thenNotAnsweredForCaller() {
            // see git history for the full body
        }
    }

    @Nested
    @DisplayName("refiling a pending proposal under another category")
    class Refile {

        @Test
        @DisplayName(
                "when called with another of the caller's categories - then the entry and stored row both carry the new category")
        void whenCalledWithAnotherCategory_thenAnswersEntryWithNewCategoryAndStoredRowCarriesIt() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when a refiled proposal is then accepted - then the recorded expense carries the category "
                + "it was refiled to")
        void whenRefiledProposalIsThenAccepted_thenRecordedExpenseCarriesTheRefiledCategory() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when the proposal was already accepted - then the answer is empty and no row is written in either table")
        void whenProposalIdWasAcceptedAMomentEarlier_thenAnswerIsEmptyAndNoRowWrittenInEitherTable() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when called for another person's proposal - then the answer is empty and their row keeps its original category")
        void whenCalledForAnotherPersonsProposal_thenAnswerIsEmptyAndTheirRowKeepsOriginalCategory() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when the id names a recorded expense, not a proposal - then the answer is empty and the expense row is untouched")
        void whenIdNamesCallersRecordedExpense_thenAnswerIsEmptyAndExpenseRowUntouched() {
            // see git history for the full body
        }
    }

    @Nested
    @DisplayName("against a mocked store, not the containerized database")
    class WithAMockedStore {

        @Test
        @DisplayName(
                "when create() hits a non-constraint failure - then throws PersistenceFailedException, not EntityNotFoundException")
        void whenCreateHitsNonConstraintDatabaseFailure_thenThrowsPersistenceFailedExceptionNotEntityNotFound() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when create() hits a constraint violation naming neither of its foreign keys - then throws PersistenceFailedException")
        void whenCreateHitsConstraintViolationNamingNeitherForeignKey_thenThrowsPersistenceFailedException() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when findSummariesByMessageReference() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenFindSummariesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when accept() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenAcceptHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when discard() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenDiscardHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            // see git history for the full body
        }

        @Test
        @DisplayName(
                "when called with an empty collection of message ids - then the answer is empty and no statement runs")
        void whenCalledWithEmptyCollectionOfMessageIds_thenAnswerIsEmptyAndNoStatementRuns() {
            // see git history for the full body
        }

        @Test
        @DisplayName("when refile() hits a database failure - then throws PersistenceFailedException wrapping it")
        void whenRefileHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt() {
            // see git history for the full body
        }
    }

    private long storedUserId(String externalId) {
        return UserRowUtils.storedUserId(userEntityRepository, externalId);
    }

    private long storedGroupingId(long userId, String name) {
        return CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, name);
    }

    private long storedCategoryId(long userId, long parentId, String name) {
        return CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, parentId, name);
    }

    private IncomingMessageId newReference() {
        return IncomingMessageId.of(UUID.randomUUID().toString());
    }

    private List<ExpenseEntity> expenseRowsFor(long userId) {
        return bot.finance.common.rows.ExpenseRowUtils.expenseRowsFor(jdbcAggregateTemplate, userId);
    }

    private Instant now() {
        return Instant.now();
    }
}
