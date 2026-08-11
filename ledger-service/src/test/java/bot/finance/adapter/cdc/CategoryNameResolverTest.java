package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bot.finance.adapter.persistence.CategoryNameReader;
import bot.finance.domain.exception.PersistenceFailedException;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CategoryNameResolverTest {

    private static final CategoryNames COFFEE_UNDER_FOOD = new CategoryNames("Coffee", "Food");
    private static final CategoryNames RENT_UNDER_HOUSING = new CategoryNames("Rent", "Housing");

    private CategoryNameReader categoryNameReader;
    private ChangeStreamMeters meters;
    private CategoryNameResolver resolver;

    @BeforeEach
    void setUp() {
        categoryNameReader = mock(CategoryNameReader.class);
        meters = mock(ChangeStreamMeters.class);
        resolver = new CategoryNameResolver(categoryNameReader, meters, properties(50_000L));
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

    @Nested
    @DisplayName("resolving a category id to its names")
    class Resolve {

        @Test
        @DisplayName("when the id is not cached - then the lookup answers the names, is called once, and a miss "
                + "is counted")
        void whenIdIsNotCached_thenLookupAnswersNamesIsCalledOnceAndMissIsCounted() {
            when(categoryNameReader.findNames(1L)).thenReturn(Optional.of(COFFEE_UNDER_FOOD));

            Optional<CategoryNames> result = resolver.resolve(1L);

            assertThat(result).contains(COFFEE_UNDER_FOOD);
            verify(categoryNameReader, times(1)).findNames(1L);
            verify(meters).countCategoryLookupMiss();
        }

        @Test
        @DisplayName("when the same id is resolved again - then it is answered from memory and the lookup is not "
                + "called again")
        void whenSameIdIsResolvedAgain_thenAnsweredFromMemoryAndLookupNotCalledAgain() {
            when(categoryNameReader.findNames(1L)).thenReturn(Optional.of(COFFEE_UNDER_FOOD));
            resolver.resolve(1L);

            Optional<CategoryNames> second = resolver.resolve(1L);

            assertThat(second).contains(COFFEE_UNDER_FOOD);
            verify(categoryNameReader, times(1)).findNames(1L);
            verify(meters).countCategoryLookupHit();
        }

        @Test
        @DisplayName("when the lookup answers nothing for the id - then an empty result is answered rather than "
                + "an exception")
        void whenLookupAnswersNothingForId_thenEmptyResultAnsweredRatherThanException() {
            when(categoryNameReader.findNames(9L)).thenReturn(Optional.empty());

            Optional<CategoryNames> result = resolver.resolve(9L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("when the lookup fails - then the failure propagates and a lookup failure is counted")
        void whenLookupFails_thenFailurePropagatesAndLookupFailureIsCounted() {
            PersistenceFailedException failure = new PersistenceFailedException("read failed", new RuntimeException());
            when(categoryNameReader.findNames(4L)).thenThrow(failure);

            assertThatThrownBy(() -> resolver.resolve(4L)).isSameAs(failure);

            verify(meters).countCategoryLookupFailure();
        }

        @Test
        @DisplayName("when the cache exceeds its configured size - then the least recently used entry is evicted")
        void whenCacheExceedsConfiguredSize_thenLeastRecentlyUsedEntryIsEvicted() {
            CategoryNameResolver smallResolver = new CategoryNameResolver(categoryNameReader, meters, properties(2L));
            when(categoryNameReader.findNames(1L)).thenReturn(Optional.of(COFFEE_UNDER_FOOD));
            when(categoryNameReader.findNames(2L)).thenReturn(Optional.of(RENT_UNDER_HOUSING));
            when(categoryNameReader.findNames(3L)).thenReturn(Optional.of(COFFEE_UNDER_FOOD));
            smallResolver.resolve(1L);
            smallResolver.resolve(2L);
            smallResolver.resolve(3L);

            smallResolver.resolve(1L);

            verify(categoryNameReader, times(2)).findNames(1L);
        }
    }

    @Nested
    @DisplayName("evicting a cached entry")
    class Evict {

        @Test
        @DisplayName(
                "when evict() is called for a cached entry's id - then the next resolve() reads the lookup " + "again")
        void whenEvictCalledForCachedEntryId_thenNextResolveReadsLookupAgain() {
            when(categoryNameReader.findNames(1L)).thenReturn(Optional.of(COFFEE_UNDER_FOOD));
            resolver.resolve(1L);

            resolver.evict(1L);
            resolver.resolve(1L);

            verify(categoryNameReader, times(2)).findNames(1L);
        }

        @Test
        @DisplayName("when evict() is called for a grouping's id - then every entry filed under it is gone too")
        void whenEvictCalledForGroupingId_thenEveryEntryFiledUnderItIsGoneToo() {
            CategoryNames rentUnderHousehold = new CategoryNames("Rent", "Household");
            CategoryNames utilitiesUnderHousehold = new CategoryNames("Utilities", "Household");
            when(categoryNameReader.findNames(10L)).thenReturn(Optional.of(rentUnderHousehold));
            when(categoryNameReader.findNames(11L)).thenReturn(Optional.of(utilitiesUnderHousehold));
            resolver.resolve(10L);
            resolver.resolve(11L);

            resolver.evict(100L);
            resolver.resolve(10L);
            resolver.resolve(11L);

            verify(categoryNameReader, times(2)).findNames(10L);
            verify(categoryNameReader, times(2)).findNames(11L);
        }
    }
}
