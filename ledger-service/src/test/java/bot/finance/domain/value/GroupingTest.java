package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidGroupingException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GroupingTest {

    @Nested
    @DisplayName("constructing a grouping")
    class GroupingConstructor {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the name is absent, empty or only whitespace - then throws InvalidGroupingException")
        void whenNameIsAbsentEmptyOrOnlyWhitespace_thenThrowsInvalidGroupingException(String name) {
            assertThatThrownBy(() -> new Grouping(name, List.of())).isInstanceOf(InvalidGroupingException.class);
        }

        @Test
        @DisplayName("when the category list is null - then throws InvalidGroupingException")
        void whenCategoryListIsNull_thenThrowsInvalidGroupingException() {
            assertThatThrownBy(() -> new Grouping("Housing", null)).isInstanceOf(InvalidGroupingException.class);
        }

        @Test
        @DisplayName("when the category list carries a null element - then throws InvalidGroupingException")
        void whenCategoryListCarriesANullElement_thenThrowsInvalidGroupingException() {
            List<Category> categoriesWithNull = new ArrayList<>();
            categoriesWithNull.add(new Category("Rent"));
            categoriesWithNull.add(null);

            assertThatThrownBy(() -> new Grouping("Housing", categoriesWithNull))
                    .isInstanceOf(InvalidGroupingException.class);
        }

        @Test
        @DisplayName("when the mutable list a grouping was built from is modified - then the grouping's categories "
                + "are unchanged")
        void whenSourceListIsModifiedAfterConstruction_thenGroupingCategoriesAreUnchanged() {
            List<Category> source = new ArrayList<>(List.of(new Category("Rent")));

            Grouping grouping = new Grouping("Housing", source);
            source.add(new Category("Mortgage"));

            assertThat(grouping.categories()).containsExactly(new Category("Rent"));
        }
    }

    @Nested
    @DisplayName("building a grouping from category names")
    class Of {

        @Test
        @DisplayName("when of() is called with a name and three category names - then returns a grouping carrying "
                + "them in order")
        void whenOfIsCalledWithNameAndThreeCategoryNames_thenReturnsGroupingWithCategoriesInOrder() {
            Grouping grouping = Grouping.of("Housing", "Rent", "Mortgage", "HOA");

            assertThat(grouping.name()).isEqualTo("Housing");
            assertThat(grouping.categories()).extracting(Category::name).containsExactly("Rent", "Mortgage", "HOA");
        }

        @Test
        @DisplayName("when of() is called with a name and no category names - then returns a grouping with no "
                + "categories")
        void whenOfIsCalledWithNameAndNoCategoryNames_thenReturnsGroupingWithEmptyCategoryList() {
            Grouping grouping = Grouping.of("Housing");

            assertThat(grouping.name()).isEqualTo("Housing");
            assertThat(grouping.categories()).isEmpty();
        }
    }

    @Nested
    @DisplayName("naming the catch-all grouping")
    class CatchAllName {

        @Test
        @DisplayName("when catchAllName() is called - then returns Miscellaneous")
        void whenCatchAllNameIsCalled_thenReturnsMiscellaneous() {
            assertThat(Grouping.catchAllName()).isEqualTo("Miscellaneous");
        }

        @Test
        @DisplayName(
                "when the names defaults() answers are filtered on catchAllName() - then exactly one grouping carries that name")
        void whenDefaultsNamesAreFilteredOnCatchAllName_thenExactlyOneGroupingCarriesThatName() {
            List<String> groupingNames =
                    Grouping.defaults().stream().map(Grouping::name).toList();

            assertThat(groupingNames)
                    .filteredOn(name -> name.equals(Grouping.catchAllName()))
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("building the predefined groupings")
    class Defaults {

        @Test
        @DisplayName("when defaults() is called - then returns the 20 predefined groupings, by name and in order")
        void whenDefaultsIsCalled_thenReturnsThe20PredefinedGroupingsByNameAndInOrder() {
            List<Grouping> groupings = Grouping.defaults();

            assertThat(groupings)
                    .extracting(Grouping::name)
                    .containsExactly(
                            "Housing",
                            "Groceries",
                            "Dining",
                            "Transportation",
                            "Utilities",
                            "Healthcare",
                            "Education",
                            "Shopping",
                            "Entertainment",
                            "Travel",
                            "Pets",
                            "Family & Children",
                            "Financial",
                            "Investments",
                            "Gifts & Donations",
                            "Work",
                            "Insurance",
                            "Personal Care",
                            "Subscriptions",
                            "Miscellaneous");
        }

        @Test
        @DisplayName(
                "when defaults() is called - then the catalogue holds 20 groupings and 77 categories, 97 names in all")
        void whenDefaultsIsCalled_thenCatalogueHolds20GroupingsAnd77CategoriesTotalling97Names() {
            List<Grouping> groupings = Grouping.defaults();
            assertThat(groupings).hasSize(20);

            int categoryCount = groupings.stream()
                    .mapToInt(grouping -> grouping.categories().size())
                    .sum();

            assertThat(categoryCount).isEqualTo(77);
            assertThat(groupings.size() + categoryCount).isEqualTo(97);
        }

        @Test
        @DisplayName("when defaults() is called - then the grouping named Housing carries its seven categories, "
                + "in order")
        void whenDefaultsIsCalled_thenHousingCarriesItsSevenCategoriesInOrder() {
            Grouping housing = groupingNamed(Grouping.defaults(), "Housing");

            assertThat(housing.categories())
                    .extracting(Category::name)
                    .containsExactly(
                            "Rent", "Mortgage", "HOA", "Property Tax", "Home Insurance", "Repairs", "Furniture");
        }

        @Test
        @DisplayName(
                "when defaults() is called - then no grouping carries two categories of the same name and no two groupings share a name")
        void whenDefaultsIsCalled_thenNoGroupingHasDuplicateCategoriesAndNoTwoGroupingsShareAName() {
            List<Grouping> groupings = Grouping.defaults();

            List<String> groupingNames = groupings.stream().map(Grouping::name).toList();
            assertThat(groupingNames).doesNotHaveDuplicates();

            assertThat(groupings).allSatisfy(grouping -> {
                List<String> categoryNames =
                        grouping.categories().stream().map(Category::name).toList();
                assertThat(categoryNames).doesNotHaveDuplicates();
            });
        }

        @Test
        @DisplayName(
                "when defaults() is called - then Travel is present both as a grouping and as a category under Insurance")
        void whenDefaultsIsCalled_thenTravelIsPresentAsGroupingAndAsCategoryUnderInsurance() {
            List<Grouping> groupings = Grouping.defaults();

            assertThat(groupings).extracting(Grouping::name).contains("Travel");

            Grouping insurance = groupingNamed(groupings, "Insurance");
            assertThat(insurance.categories()).extracting(Category::name).contains("Travel");
        }

        private Grouping groupingNamed(List<Grouping> groupings, String name) {
            return groupings.stream()
                    .filter(grouping -> grouping.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no grouping named " + name));
        }
    }
}
