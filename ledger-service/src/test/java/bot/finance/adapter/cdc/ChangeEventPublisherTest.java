package bot.finance.adapter.cdc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import bot.finance.adapter.redis.RedisChangeStreamWriter;
import io.debezium.engine.ChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ChangeEventPublisherTest {

    private RedisChangeStreamWriter redisChangeStreamWriter;
    private ChangeStreamMeters meters;
    private ChangeEventPublisher publisher;

    @BeforeEach
    void setUp() {
        redisChangeStreamWriter = mock(RedisChangeStreamWriter.class);
        meters = mock(ChangeStreamMeters.class);
        publisher = new ChangeEventPublisher(redisChangeStreamWriter, meters);
    }

    private static ChangeEvent<String, String> changeEvent(String value) {
        @SuppressWarnings("unchecked")
        ChangeEvent<String, String> event = mock(ChangeEvent.class);
        when(event.value()).thenReturn(value);
        return event;
    }

    @Nested
    @DisplayName("publishing a captured change event")
    class Publish {

        @Test
        @Disabled("RU02: the publisher resolves no names and forwards an outbox insert's four columns")
        @DisplayName("when an expense update is published - then one entry is written enriched with both categories")
        void whenExpenseUpdateIsPublished_thenOneEntryWrittenEnrichedWithBothCategories() {
            // rewritten against an outbox insert
        }

        @Test
        @Disabled("RU02: the publisher resolves no names and forwards an outbox insert's four columns")
        @DisplayName("when an expense insert is published - then the enrichment block carries the after side alone")
        void whenExpenseInsertIsPublished_thenEnrichmentBlockCarriesAfterSideAlone() {
            // rewritten against an outbox insert
        }

        @Test
        @Disabled("RU02: the publisher resolves no names and forwards an outbox insert's four columns")
        @DisplayName(
                "when an expense delete is published - then the enrichment block carries the before side " + "alone")
        void whenExpenseDeleteIsPublished_thenEnrichmentBlockCarriesBeforeSideAlone() {
            // rewritten against an outbox insert
        }

        @Test
        @Disabled("RU02: the publisher resolves no names and forwards an outbox insert's four columns")
        @DisplayName("when an expense delete's category resolves to nothing - then no names are written and "
                + "publish answers published")
        void whenExpenseDeleteCategoryResolvesToNothing_thenNoNamesWrittenAndPublishAnswersPublished() {
            // rewritten against an outbox insert
        }

        @Test
        @Disabled("RU02: category events no longer reach the slot, so the resolver premise is gone")
        @DisplayName(
                "when a category update is published - then the resolver takes the row and no enrichment " + "is added")
        void whenCategoryUpdateIsPublished_thenResolverTakesRowAndNoEnrichmentIsAdded() {
            // deleted - a category event no longer reaches the publisher
        }

        @Test
        @Disabled("RU02: category events no longer reach the slot, so the resolver premise is gone")
        @DisplayName("when a category filed under a grouping is published - then the row carries its parent id")
        void whenCategoryFiledUnderAGroupingIsPublished_thenRowCarriesItsParentId() {
            // deleted - a category event no longer reaches the publisher
        }

        @Test
        @Disabled("RU02: category events no longer reach the slot, so the resolver premise is gone")
        @DisplayName(
                "when a category delete is published - then the resolver drops that id rather than storing " + "a row")
        void whenCategoryDeleteIsPublished_thenResolverDropsThatIdRatherThanStoringARow() {
            // deleted - a category event no longer reaches the publisher
        }

        @Test
        @Disabled("RU02: the publisher resolves no names, so a resolver failure is not a scenario it has anymore")
        @DisplayName("when the resolver fails the lookup - then nothing is written and publish answers not published")
        void whenResolverFailsLookup_thenNothingWrittenAndPublishAnswersNotPublished() {
            // rewritten against an outbox insert
        }

        @Test
        @Disabled("RU02: the publisher resolves no names and forwards an outbox insert's four columns")
        @DisplayName("when the writer refuses - then publish answers not published and a publish failure is counted")
        void whenWriterRefuses_thenPublishAnswersNotPublishedAndPublishFailureCounted() {
            // rewritten against an outbox insert
        }

        @Test
        @Disabled("RU02: the published counter now carries one type tag rather than a table and an op")
        @DisplayName("when an event publishes - then the published counter is tagged and the lag gauge is set "
                + "from source.ts_ms")
        void whenEventPublishes_thenPublishedCounterTaggedAndLagGaugeSetFromSourceTsMs() {
            // rewritten against an outbox insert
        }
    }
}
