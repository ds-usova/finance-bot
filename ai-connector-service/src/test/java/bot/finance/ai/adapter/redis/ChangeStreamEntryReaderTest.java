package bot.finance.ai.adapter.redis;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChangeStreamEntryReaderTest {

    private final ChangeStreamEntryReader reader = new ChangeStreamEntryReader();

    @Nested
    @DisplayName("read()")
    class Read {

        @Disabled("RU05: rewritten against the event catalogue - type maps to status, payload is the row")
        @Test
        @DisplayName("when a proposal c body carries enrichment - then the command holds a PROPOSAL CREATED "
                + "change built from it")
        void whenProposalCreatedBodyCarriesEnrichment_thenCommandHoldsProposalCreatedChange() {}

        @Disabled("RU05: the pending-delete change no longer exists - a ProposalDiscarded event is DISCARDED")
        @Test
        @DisplayName("when a proposal d body is read - then the change is DELETED with a before row and no after")
        void whenProposalDeletedBodyIsRead_thenChangeIsDeletedWithBeforeRowAndNoAfter() {}

        @Disabled("RU05: the before/after change shape no longer exists - one entry is one event")
        @Test
        @DisplayName("when an expense u body's enrichment names differ before and after - then each row keeps its "
                + "own names")
        void whenExpenseUpdatedBodyHasDifferentEnrichmentNames_thenBeforeAndAfterHoldTheirOwnNames() {}

        @Disabled("RU05: rewritten against the event catalogue's incomingMessageId: null scenario")
        @Test
        @DisplayName("when an expense c body's row has a null incoming_message_id - then the after row's message "
                + "id is empty")
        void whenExpenseCreatedBodyHasNullIncomingMessageId_thenAfterRowMessageIdIsEmpty() {}

        @Disabled("RU05: enrichment no longer exists - category and grouping are read from the event's own payload")
        @Test
        @DisplayName("when a spending body has no enrichment block - then both names are empty on every row")
        void whenSpendingBodyHasNoEnrichmentBlock_thenBothNamesAreEmptyOnEveryRow() {}

        @Disabled("RU05: category and grouping events do not exist yet - only the six spending types are read")
        @Test
        @DisplayName("when a category u body is read - then the after row's parentId matches what the row carried")
        void whenCategoryUpdatedBodyIsRead_thenAfterRowParentIdMatchesRow() {}

        @Disabled("RU05: rewritten against an entry whose type is none of the six")
        @Test
        @DisplayName("when the body is an r op or names a table this reader ignores - then it answers empty")
        void whenBodyIsIgnored_thenAnswersEmpty() {}

        @Disabled("RU05: rewritten against the event catalogue's invalid-body scenarios")
        @Test
        @DisplayName("when the body is not a valid change event - then InvalidValueException is thrown")
        void whenBodyIsNotValidChangeEvent_thenInvalidValueExceptionIsThrown() {}
    }
}
