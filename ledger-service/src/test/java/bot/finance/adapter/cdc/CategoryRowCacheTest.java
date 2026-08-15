package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CategoryRowCacheTest {

    private static final CategoryRow COFFEE = new CategoryRow("Coffee", Optional.of(10L));
    private static final CategoryRow FOOD = new CategoryRow("Food", Optional.empty());

    @Nested
    @DisplayName("looking an id up")
    class Lookup {

        @Test
        @DisplayName("when the id was never stored - then nothing is answered")
        void whenIdWasNeverStored_thenNothingIsAnswered() {
            CategoryRowCache cache = new CategoryRowCache(10);

            assertThat(cache.lookup(1L)).isEmpty();
        }

        @Test
        @DisplayName("when a row was stored under the id - then that row is answered")
        void whenRowWasStoredUnderId_thenThatRowIsAnswered() {
            CategoryRowCache cache = new CategoryRowCache(10);
            cache.put(1L, Optional.of(COFFEE));

            assertThat(cache.lookup(1L)).contains(Optional.of(COFFEE));
        }

        @Test
        @DisplayName("when the id was stored as carrying no row - then it is answered as stored and empty")
        void whenIdWasStoredAsCarryingNoRow_thenAnsweredAsStoredAndEmpty() {
            CategoryRowCache cache = new CategoryRowCache(10);
            cache.put(9L, Optional.empty());

            assertThat(cache.lookup(9L)).isPresent().get().isEqualTo(Optional.empty());
        }
    }

    @Nested
    @DisplayName("removing an id")
    class Remove {

        @Test
        @DisplayName("when a stored id is removed - then it is no longer held")
        void whenStoredIdIsRemoved_thenNoLongerHeld() {
            CategoryRowCache cache = new CategoryRowCache(10);
            cache.put(1L, Optional.of(COFFEE));

            cache.remove(1L);

            assertThat(cache.lookup(1L)).isEmpty();
        }

        @Test
        @DisplayName("when an id nothing was stored under is removed - then every other id is still held")
        void whenUnstoredIdIsRemoved_thenEveryOtherIdIsStillHeld() {
            CategoryRowCache cache = new CategoryRowCache(10);
            cache.put(1L, Optional.of(COFFEE));
            cache.put(10L, Optional.of(FOOD));

            cache.remove(99L);

            assertThat(cache.lookup(1L)).contains(Optional.of(COFFEE));
            assertThat(cache.lookup(10L)).contains(Optional.of(FOOD));
        }
    }

    @Nested
    @DisplayName("holding to its bound")
    class Bound {

        @Test
        @DisplayName("when the bound is passed - then the least recently used id is dropped")
        void whenBoundIsPassed_thenLeastRecentlyUsedIdIsDropped() {
            CategoryRowCache cache = new CategoryRowCache(2);
            cache.put(1L, Optional.of(COFFEE));
            cache.put(10L, Optional.of(FOOD));

            cache.put(2L, Optional.empty());

            assertThat(cache.lookup(1L)).isEmpty();
            assertThat(cache.lookup(10L)).contains(Optional.of(FOOD));
            assertThat(cache.lookup(2L)).contains(Optional.empty());
        }

        @Test
        @DisplayName("when a stored id is looked up again - then a later store drops a different id")
        void whenStoredIdIsLookedUpAgain_thenLaterStoreDropsADifferentId() {
            CategoryRowCache cache = new CategoryRowCache(2);
            cache.put(1L, Optional.of(COFFEE));
            cache.put(10L, Optional.of(FOOD));
            cache.lookup(1L);

            cache.put(2L, Optional.empty());

            assertThat(cache.lookup(1L)).contains(Optional.of(COFFEE));
            assertThat(cache.lookup(10L)).isEmpty();
        }
    }
}
