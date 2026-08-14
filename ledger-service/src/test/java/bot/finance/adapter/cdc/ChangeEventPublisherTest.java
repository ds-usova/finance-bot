package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import bot.finance.adapter.redis.RedisChangeStreamWriter;
import bot.finance.common.fixtures.JsonUtils;
import bot.finance.domain.exception.PersistenceFailedException;
import com.fasterxml.jackson.databind.JsonNode;
import io.debezium.engine.ChangeEvent;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChangeEventPublisherTest {

    private CategoryNameResolver categoryNameResolver;
    private RedisChangeStreamWriter redisChangeStreamWriter;
    private ChangeStreamMeters meters;
    private ChangeEventPublisher publisher;

    @BeforeEach
    void setUp() {
        categoryNameResolver = mock(CategoryNameResolver.class);
        redisChangeStreamWriter = mock(RedisChangeStreamWriter.class);
        meters = mock(ChangeStreamMeters.class);
        publisher = new ChangeEventPublisher(categoryNameResolver, redisChangeStreamWriter, meters);
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
        @DisplayName("when an expense update is published - then one entry is written enriched with both categories")
        void whenExpenseUpdateIsPublished_thenOneEntryWrittenEnrichedWithBothCategories() {
            String payload =
                    """
                    {"before":{"category_id":1},"after":{"category_id":2},\
                    "source":{"table":"expense","ts_ms":1700000000000},"op":"u"}""";
            ChangeEvent<String, String> event = changeEvent(payload);
            when(categoryNameResolver.resolve(1L)).thenReturn(Optional.of(new CategoryNames("Coffee", "Food")));
            when(categoryNameResolver.resolve(2L)).thenReturn(Optional.of(new CategoryNames("Rent", "Housing")));
            when(redisChangeStreamWriter.write(anyString(), any())).thenReturn(true);

            boolean result = publisher.publish(event);

            assertThat(result).isTrue();
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Optional<String>> enrichmentCaptor = ArgumentCaptor.forClass(Optional.class);
            verify(redisChangeStreamWriter, times(1)).write(payloadCaptor.capture(), enrichmentCaptor.capture());
            assertThat(payloadCaptor.getValue()).isEqualTo(payload);
            JsonNode enrichment = JsonUtils.readJson(enrichmentCaptor.getValue().orElseThrow());
            assertThat(enrichment.path("before").path("categoryName").asText()).isEqualTo("Coffee");
            assertThat(enrichment.path("before").path("groupingName").asText()).isEqualTo("Food");
            assertThat(enrichment.path("after").path("categoryName").asText()).isEqualTo("Rent");
            assertThat(enrichment.path("after").path("groupingName").asText()).isEqualTo("Housing");
        }

        @Test
        @DisplayName("when an expense_proposal insert is published - then the enrichment block carries the after "
                + "side alone")
        void whenExpenseProposalInsertIsPublished_thenEnrichmentBlockCarriesAfterSideAlone() {
            String payload =
                    """
                    {"after":{"category_id":5},"source":{"table":"expense_proposal","ts_ms":1700000000000},"op":"c"}""";
            ChangeEvent<String, String> event = changeEvent(payload);
            when(categoryNameResolver.resolve(5L)).thenReturn(Optional.of(new CategoryNames("Coffee", "Food")));
            when(redisChangeStreamWriter.write(anyString(), any())).thenReturn(true);

            publisher.publish(event);

            ArgumentCaptor<Optional<String>> enrichmentCaptor = enrichmentArgumentCaptor();
            verify(redisChangeStreamWriter).write(anyString(), enrichmentCaptor.capture());
            JsonNode enrichment = JsonUtils.readJson(enrichmentCaptor.getValue().orElseThrow());
            assertThat(enrichment.path("after").path("categoryName").asText()).isEqualTo("Coffee");
            assertThat(enrichment.has("before")).isFalse();
        }

        @Test
        @DisplayName(
                "when an expense delete is published - then the enrichment block carries the before side " + "alone")
        void whenExpenseDeleteIsPublished_thenEnrichmentBlockCarriesBeforeSideAlone() {
            String payload =
                    """
                    {"before":{"category_id":7},"source":{"table":"expense","ts_ms":1700000000000},"op":"d"}""";
            ChangeEvent<String, String> event = changeEvent(payload);
            when(categoryNameResolver.resolve(7L)).thenReturn(Optional.of(new CategoryNames("Rent", "Housing")));
            when(redisChangeStreamWriter.write(anyString(), any())).thenReturn(true);

            publisher.publish(event);

            ArgumentCaptor<Optional<String>> enrichmentCaptor = enrichmentArgumentCaptor();
            verify(redisChangeStreamWriter).write(anyString(), enrichmentCaptor.capture());
            JsonNode enrichment = JsonUtils.readJson(enrichmentCaptor.getValue().orElseThrow());
            assertThat(enrichment.path("before").path("categoryName").asText()).isEqualTo("Rent");
            assertThat(enrichment.has("after")).isFalse();
        }

        @Test
        @DisplayName("when an expense delete's category resolves to nothing - then no names are written and "
                + "publish answers published")
        void whenExpenseDeleteCategoryResolvesToNothing_thenNoNamesWrittenAndPublishAnswersPublished() {
            String payload =
                    """
                    {"before":{"category_id":9},"source":{"table":"expense","ts_ms":1700000000000},"op":"d"}""";
            ChangeEvent<String, String> event = changeEvent(payload);
            when(categoryNameResolver.resolve(9L)).thenReturn(Optional.empty());
            when(redisChangeStreamWriter.write(anyString(), any())).thenReturn(true);

            boolean result = publisher.publish(event);

            assertThat(result).isTrue();
            ArgumentCaptor<Optional<String>> enrichmentCaptor = enrichmentArgumentCaptor();
            verify(redisChangeStreamWriter).write(anyString(), enrichmentCaptor.capture());
            String enrichmentJson = enrichmentCaptor.getValue().orElse("");
            assertThat(enrichmentJson).doesNotContain("categoryName").doesNotContain("groupingName");
        }

        @Test
        @DisplayName(
                "when a category update is published - then the resolver takes the row and no enrichment " + "is added")
        void whenCategoryUpdateIsPublished_thenResolverTakesRowAndNoEnrichmentIsAdded() {
            String payload =
                    """
                    {"after":{"id":42,"name":"Household","parent_id":null},\
                    "source":{"table":"category","ts_ms":1700000000000},"op":"u"}""";
            ChangeEvent<String, String> event = changeEvent(payload);
            when(redisChangeStreamWriter.write(anyString(), any())).thenReturn(true);

            publisher.publish(event);

            verify(categoryNameResolver).refresh(42L, new CategoryRow("Household", Optional.empty()));
            verify(categoryNameResolver, never()).resolve(anyLong());
            verify(redisChangeStreamWriter).write(payload, Optional.empty());
        }

        @Test
        @DisplayName("when a category filed under a grouping is published - then the row carries its parent id")
        void whenCategoryFiledUnderAGroupingIsPublished_thenRowCarriesItsParentId() {
            String payload =
                    """
                    {"after":{"id":7,"name":"Coffee","parent_id":42},\
                    "source":{"table":"category","ts_ms":1700000000000},"op":"c"}""";
            ChangeEvent<String, String> event = changeEvent(payload);
            when(redisChangeStreamWriter.write(anyString(), any())).thenReturn(true);

            publisher.publish(event);

            verify(categoryNameResolver).refresh(7L, new CategoryRow("Coffee", Optional.of(42L)));
        }

        @Test
        @DisplayName(
                "when a category delete is published - then the resolver drops that id rather than storing " + "a row")
        void whenCategoryDeleteIsPublished_thenResolverDropsThatIdRatherThanStoringARow() {
            String payload =
                    """
                    {"before":{"id":42,"name":"Household","parent_id":null},\
                    "source":{"table":"category","ts_ms":1700000000000},"op":"d"}""";
            ChangeEvent<String, String> event = changeEvent(payload);
            when(redisChangeStreamWriter.write(anyString(), any())).thenReturn(true);

            publisher.publish(event);

            verify(categoryNameResolver).evict(42L);
            verify(categoryNameResolver, never()).refresh(anyLong(), any());
        }

        @Test
        @DisplayName("when the resolver fails the lookup - then nothing is written and publish answers not published")
        void whenResolverFailsLookup_thenNothingWrittenAndPublishAnswersNotPublished() {
            String payload =
                    """
                    {"before":{"category_id":1},"after":{"category_id":2},\
                    "source":{"table":"expense","ts_ms":1700000000000},"op":"u"}""";
            ChangeEvent<String, String> event = changeEvent(payload);
            when(categoryNameResolver.resolve(1L))
                    .thenThrow(new PersistenceFailedException("read failed", new RuntimeException()));

            boolean result = publisher.publish(event);

            assertThat(result).isFalse();
            verifyNoInteractions(redisChangeStreamWriter);
        }

        @Test
        @DisplayName("when the writer refuses - then publish answers not published and a publish failure is counted")
        void whenWriterRefuses_thenPublishAnswersNotPublishedAndPublishFailureCounted() {
            String payload =
                    """
                    {"after":{"category_id":2},"source":{"table":"expense_proposal","ts_ms":1700000000000},"op":"c"}""";
            ChangeEvent<String, String> event = changeEvent(payload);
            when(categoryNameResolver.resolve(2L)).thenReturn(Optional.of(new CategoryNames("Rent", "Housing")));
            when(redisChangeStreamWriter.write(anyString(), any())).thenReturn(false);

            boolean result = publisher.publish(event);

            assertThat(result).isFalse();
            verify(meters).countPublishFailure();
        }

        @Test
        @DisplayName("when an event publishes - then the published counter is tagged and the lag gauge is set "
                + "from source.ts_ms")
        void whenEventPublishes_thenPublishedCounterTaggedAndLagGaugeSetFromSourceTsMs() {
            String payload =
                    """
                    {"after":{"category_id":2},"source":{"table":"expense_proposal","ts_ms":1700000000000},"op":"c"}""";
            ChangeEvent<String, String> event = changeEvent(payload);
            when(categoryNameResolver.resolve(2L)).thenReturn(Optional.of(new CategoryNames("Rent", "Housing")));
            when(redisChangeStreamWriter.write(anyString(), any())).thenReturn(true);

            publisher.publish(event);

            verify(meters).countPublished(eq("expense_proposal"), eq("c"));
            ArgumentCaptor<Instant> lagCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(meters).setEventLag(lagCaptor.capture());
            assertThat(lagCaptor.getValue()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
        }

        private ArgumentCaptor<Optional<String>> enrichmentArgumentCaptor() {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Optional<String>> captor = ArgumentCaptor.forClass(Optional.class);
            return captor;
        }
    }
}
