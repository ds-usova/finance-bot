package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.adapter.persistence.CategoryRowReader;
import bot.finance.domain.exception.PersistenceFailedException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CategoryNameResolverTest {

    private static final long FOOD_ID = 10L;
    private static final long HOUSING_ID = 20L;
    private static final CategoryRow FOOD = new CategoryRow("Food", Optional.empty());
    private static final CategoryRow HOUSING = new CategoryRow("Housing", Optional.empty());
    private static final CategoryRow COFFEE = new CategoryRow("Coffee", Optional.of(FOOD_ID));
    private static final CategoryRow UTILITIES = new CategoryRow("Utilities", Optional.of(FOOD_ID));
    private static final CategoryRow RENT = new CategoryRow("Rent", Optional.of(HOUSING_ID));

    private CategoryRowReader categoryRowReader;
    private ChangeStreamMeters meters;
    private CategoryNameResolver resolver;

    @BeforeEach
    void setUp() {
        categoryRowReader = mock(CategoryRowReader.class);
        meters = mock(ChangeStreamMeters.class);
        resolver = new CategoryNameResolver(categoryRowReader, meters, properties(50_000L));
    }

    private static CdcProperties properties(long categoryCacheSize) {
        return new CdcProperties(
                true,
                "ledger_slot",
                "ledger.cdc",
                10_000L,
                "never",
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                categoryCacheSize,
                "secret");
    }

    private void storedRows() {
        when(categoryRowReader.findRow(1L)).thenReturn(Optional.of(COFFEE));
        when(categoryRowReader.findRow(2L)).thenReturn(Optional.of(RENT));
        when(categoryRowReader.findRow(3L)).thenReturn(Optional.of(UTILITIES));
        when(categoryRowReader.findRow(FOOD_ID)).thenReturn(Optional.of(FOOD));
        when(categoryRowReader.findRow(HOUSING_ID)).thenReturn(Optional.of(HOUSING));
    }

    @Nested
    @DisplayName("resolving a category id to its names")
    class Resolve {

        @Test
        @DisplayName("when the id is not cached - then both rows are read and the two names are answered")
        void whenIdIsNotCached_thenBothRowsAreReadAndTwoNamesAnswered() {
            storedRows();

            Optional<CategoryNames> result = resolver.resolve(1L);

            assertThat(result).contains(new CategoryNames("Coffee", "Food"));
            verify(categoryRowReader, times(1)).findRow(1L);
            verify(categoryRowReader, times(1)).findRow(FOOD_ID);
            verify(meters).countCategoryLookupMiss();
        }

        @Test
        @DisplayName("when the same id is resolved again - then it is answered from memory and nothing is read")
        void whenSameIdIsResolvedAgain_thenAnsweredFromMemoryAndNothingIsRead() {
            storedRows();
            resolver.resolve(1L);

            Optional<CategoryNames> second = resolver.resolve(1L);

            assertThat(second).contains(new CategoryNames("Coffee", "Food"));
            verify(categoryRowReader, times(1)).findRow(1L);
            verify(categoryRowReader, times(1)).findRow(FOOD_ID);
            verify(meters).countCategoryLookupHit();
        }

        @Test
        @DisplayName("when a second category under the same grouping is resolved - then the grouping is read once")
        void whenSecondCategoryUnderSameGroupingIsResolved_thenGroupingIsReadOnce() {
            storedRows();
            resolver.resolve(1L);

            Optional<CategoryNames> second = resolver.resolve(3L);

            assertThat(second).contains(new CategoryNames("Utilities", "Food"));
            verify(categoryRowReader, times(1)).findRow(FOOD_ID);
        }

        @Test
        @DisplayName("when no row carries the id - then an empty result is answered rather than an exception")
        void whenNoRowCarriesId_thenEmptyResultAnsweredRatherThanException() {
            when(categoryRowReader.findRow(9L)).thenReturn(Optional.empty());

            Optional<CategoryNames> result = resolver.resolve(9L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("when the id is a grouping's own id - then nothing is answered and no second row is read")
        void whenIdIsAGroupingsOwnId_thenNothingIsAnsweredAndNoSecondRowIsRead() {
            storedRows();

            Optional<CategoryNames> result = resolver.resolve(FOOD_ID);

            assertThat(result).isEmpty();
            verify(categoryRowReader, times(1)).findRow(FOOD_ID);
        }

        @Test
        @DisplayName("when the lookup fails - then the failure propagates and a lookup failure is counted")
        void whenLookupFails_thenFailurePropagatesAndLookupFailureIsCounted() {
            PersistenceFailedException failure = new PersistenceFailedException("read failed", new RuntimeException());
            when(categoryRowReader.findRow(4L)).thenThrow(failure);

            assertThatThrownBy(() -> resolver.resolve(4L)).isSameAs(failure);

            verify(meters).countCategoryLookupFailure();
        }

        @Test
        @DisplayName("when the cache exceeds its configured size - then the least recently used row is evicted")
        void whenCacheExceedsConfiguredSize_thenLeastRecentlyUsedRowIsEvicted() {
            CategoryNameResolver smallResolver = new CategoryNameResolver(categoryRowReader, meters, properties(2L));
            storedRows();
            smallResolver.resolve(1L);
            smallResolver.resolve(2L);

            smallResolver.resolve(1L);

            verify(categoryRowReader, times(2)).findRow(1L);
        }
    }

    @Nested
    @DisplayName("evicting a cached entry")
    class Evict {

        @Test
        @DisplayName("when evict() is called for a cached row's id - then the next resolve() reads it again")
        void whenEvictCalledForCachedRowId_thenNextResolveReadsItAgain() {
            storedRows();
            resolver.resolve(1L);

            resolver.evict(1L);
            resolver.resolve(1L);

            verify(categoryRowReader, times(2)).findRow(1L);
        }

        @Test
        @DisplayName("when evict() is called for a grouping's id - then only that grouping's row is read again")
        void whenEvictCalledForGroupingId_thenOnlyThatGroupingsRowIsReadAgain() {
            storedRows();
            resolver.resolve(1L);
            resolver.resolve(3L);

            resolver.evict(FOOD_ID);
            resolver.resolve(1L);
            resolver.resolve(3L);

            verify(categoryRowReader, times(2)).findRow(FOOD_ID);
            verify(categoryRowReader, times(1)).findRow(1L);
            verify(categoryRowReader, times(1)).findRow(3L);
        }

        @Test
        @DisplayName("when evict() is called for an id nothing cached carries - then every other row stays cached")
        void whenEvictCalledForUncachedId_thenEveryOtherRowStaysCached() {
            storedRows();
            resolver.resolve(1L);
            resolver.resolve(2L);

            resolver.evict(99L);
            resolver.resolve(1L);
            resolver.resolve(2L);

            verify(categoryRowReader, times(1)).findRow(1L);
            verify(categoryRowReader, times(1)).findRow(2L);
        }
    }
}
