package bot.finance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EntityTest {

    static class StoredThing extends Entity {
        StoredThing(Long id) {
            super(id);
        }
    }

    static class OtherThing extends Entity {
        OtherThing(Long id) {
            super(id);
        }
    }

    @Nested
    @DisplayName("comparing entities for equality")
    class Equality {

        @Test
        @DisplayName("when two instances of the same subclass carry the same id - then they are equal")
        void whenTwoInstancesOfSameSubclassCarrySameId_thenTheyAreEqual() {
            StoredThing first = new StoredThing(1L);
            StoredThing second = new StoredThing(1L);

            assertThat(first).isEqualTo(second);
        }

        @Test
        @DisplayName("when two instances of the same subclass carry different ids - then they are not equal")
        void whenTwoInstancesOfSameSubclassCarryDifferentIds_thenTheyAreNotEqual() {
            StoredThing first = new StoredThing(1L);
            StoredThing second = new StoredThing(2L);

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName(
                "when two instances of different subclasses carry the same id - then they are not equal, the concrete class is part of the identity")
        void whenTwoInstancesOfDifferentSubclassesCarrySameId_thenTheyAreNotEqual() {
            StoredThing first = new StoredThing(1L);
            OtherThing second = new OtherThing(1L);

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName(
                "when two instances of the same subclass both have an absent id - then they are not equal, and each equals itself")
        void whenTwoInstancesOfSameSubclassBothHaveAbsentId_thenTheyAreNotEqualAndEachEqualsItself() {
            StoredThing first = new StoredThing(null);
            StoredThing second = new StoredThing(null);

            assertThat(first).isNotEqualTo(second);
            assertThat(first).isEqualTo(first);
            assertThat(second).isEqualTo(second);
        }

        @Test
        @DisplayName(
                "when an instance with an absent id is compared with another of the same subclass carrying an id, in both directions - then they are not equal")
        void whenInstanceWithAbsentIdIsComparedWithInstanceCarryingId_thenTheyAreNotEqual() {
            StoredThing withoutId = new StoredThing(null);
            StoredThing withId = new StoredThing(1L);

            assertThat(withoutId).isNotEqualTo(withId);
            assertThat(withId).isNotEqualTo(withoutId);
        }

        @Test
        @DisplayName(
                "when an instance is compared with null and with an unrelated object - then it is not equal to either")
        void whenInstanceIsComparedWithNullAndUnrelatedObject_thenItIsNotEqualToEither() {
            StoredThing thing = new StoredThing(1L);

            assertThat(thing).isNotEqualTo(null);
            assertThat(thing).isNotEqualTo("an unrelated object");
        }
    }

    @Nested
    @DisplayName("hashing entities")
    class Hashing {

        @Test
        @DisplayName("when two instances of the same subclass carry the same id - then their hash codes match")
        void whenTwoInstancesOfSameSubclassCarrySameId_thenHashCodesMatch() {
            StoredThing first = new StoredThing(1L);
            StoredThing second = new StoredThing(1L);

            assertThat(first.hashCode()).isEqualTo(second.hashCode());
        }

        @Test
        @DisplayName(
                "when an instance has an absent id - then its hash code, taken twice, both equal System.identityHashCode of that instance")
        void whenInstanceHasAbsentId_thenHashCodeEqualsIdentityHashCode() {
            StoredThing thing = new StoredThing(null);

            assertThat(thing.hashCode()).isEqualTo(System.identityHashCode(thing));
            assertThat(thing.hashCode()).isEqualTo(System.identityHashCode(thing));
        }

        @Test
        @DisplayName("when two instances both have an absent id - then their hash codes differ")
        void whenTwoInstancesBothHaveAbsentId_thenHashCodesDiffer() {
            StoredThing first = new StoredThing(null);
            StoredThing second = new StoredThing(null);

            assertThat(first.hashCode()).isNotEqualTo(second.hashCode());
        }
    }

    @Nested
    @DisplayName("reading an entity's id")
    class Id {

        @Test
        @DisplayName(
                "when an instance is built with an id and another without - then the first contains that id and the second is empty")
        void whenInstanceIsBuiltWithIdAndAnotherWithout_thenFirstContainsIdAndSecondIsEmpty() {
            StoredThing withId = new StoredThing(1L);
            StoredThing withoutId = new StoredThing(null);

            assertThat(withId.id()).contains(1L);
            assertThat(withoutId.id()).isEmpty();
        }
    }
}
