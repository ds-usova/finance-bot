package bot.finance.domain.value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.exception.InvalidCategoryException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryTest {

    @Nested
    @DisplayName("constructing a category")
    class CategoryConstructor {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("when the name is absent, empty, or only whitespace - then throws InvalidCategoryException")
        void whenNameIsAbsentEmptyOrOnlyWhitespace_thenThrowsInvalidCategoryException(String name) {
            assertThatThrownBy(() -> new Category(name, List.of())).isInstanceOf(InvalidCategoryException.class);
        }

        @Test
        @DisplayName("when the child list is absent - then throws InvalidCategoryException")
        void whenChildListIsAbsent_thenThrowsInvalidCategoryException() {
            assertThatThrownBy(() -> new Category("Groceries", null)).isInstanceOf(InvalidCategoryException.class);
        }

        @Test
        @DisplayName("when the child list carries a null element - then throws InvalidCategoryException")
        void whenChildListCarriesANullElement_thenThrowsInvalidCategoryException() {
            List<Category> childrenWithNull = new ArrayList<>();
            childrenWithNull.add(Category.leaf("Rent"));
            childrenWithNull.add(null);

            assertThatThrownBy(() -> new Category("Housing", childrenWithNull))
                    .isInstanceOf(InvalidCategoryException.class);
        }

        @Test
        @DisplayName(
                "when a child carries children of its own - then throws InvalidCategoryException, the tree is exactly two levels")
        void whenAChildCarriesChildrenOfItsOwn_thenThrowsInvalidCategoryException() {
            Category grandchild = Category.leaf("Grandchild");
            Category childWithChildren = new Category("Child", List.of(grandchild));

            assertThatThrownBy(() -> new Category("Group", List.of(childWithChildren)))
                    .isInstanceOf(InvalidCategoryException.class);
        }

        @Test
        @DisplayName(
                "when the record is constructed with a mutable child list and the source list is then modified - then the category's children are unchanged")
        void whenSourceListIsModifiedAfterConstruction_thenCategoryChildrenAreUnchanged() {
            List<Category> source = new ArrayList<>(List.of(Category.leaf("Rent")));

            Category category = new Category("Housing", source);
            source.add(Category.leaf("Mortgage"));

            assertThat(category.children()).containsExactly(Category.leaf("Rent"));
        }
    }

    @Nested
    @DisplayName("building a leaf category")
    class Leaf {

        @Test
        @DisplayName("when leaf() is called - then returns a category with that name and no children")
        void whenLeafIsCalled_thenReturnsCategoryWithThatNameAndNoChildren() {
            Category leaf = Category.leaf("Rent");

            assertThat(leaf.name()).isEqualTo("Rent");
            assertThat(leaf.children()).isEmpty();
        }
    }

    @Nested
    @DisplayName("building a group category")
    class Group {

        @Test
        @DisplayName(
                "when group() is called - then returns a category with that name whose children are childless categories with those names, in the order given")
        void whenGroupIsCalled_thenReturnsCategoryWithChildlessChildrenInOrder() {
            Category group = Category.group("Housing", "Rent", "Mortgage", "HOA");

            assertThat(group.name()).isEqualTo("Housing");
            assertThat(group.children()).extracting(Category::name).containsExactly("Rent", "Mortgage", "HOA");
            assertThat(group.children())
                    .allSatisfy(child -> assertThat(child.children()).isEmpty());
        }
    }

    @Nested
    @DisplayName("building the predefined category tree")
    class Defaults {

        @Test
        @DisplayName("when defaults() is called - then returns the 20 predefined groups, by name and in order")
        void whenDefaultsIsCalled_thenReturnsThe20PredefinedGroupsByNameAndInOrder() {
            List<Category> groups = Category.defaults();

            assertThat(groups)
                    .extracting(Category::name)
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
        @DisplayName("when defaults() is called - then the whole tree holds 97 categories, of which 77 are children")
        void whenDefaultsIsCalled_thenTheWholeTreeHolds97CategoriesOf77AreChildren() {
            List<Category> groups = Category.defaults();
            assertThat(groups).hasSize(20);

            int childCount =
                    groups.stream().mapToInt(group -> group.children().size()).sum();

            assertThat(childCount).isEqualTo(77);
            assertThat(groups.size() + childCount).isEqualTo(97);
        }

        @Test
        @DisplayName(
                "when defaults() is called - then the group named Housing carries exactly Rent, Mortgage, HOA, Property Tax, Home Insurance, Repairs and Furniture, in that order")
        void whenDefaultsIsCalled_thenHousingCarriesItsSevenChildrenInOrder() {
            Category housing = groupNamed(Category.defaults(), "Housing");

            assertThat(housing.children())
                    .extracting(Category::name)
                    .containsExactly(
                            "Rent", "Mortgage", "HOA", "Property Tax", "Home Insurance", "Repairs", "Furniture");
        }

        @Test
        @DisplayName(
                "when defaults() is called - then no group carries two children with the same name, and no two groups share a name")
        void whenDefaultsIsCalled_thenNoGroupHasDuplicateChildrenAndNoTwoGroupsShareAName() {
            List<Category> groups = Category.defaults();
            assertThat(groups).hasSize(20);

            List<String> groupNames = groups.stream().map(Category::name).toList();
            assertThat(groupNames).doesNotHaveDuplicates();

            assertThat(groups).allSatisfy(group -> {
                List<String> childNames =
                        group.children().stream().map(Category::name).toList();
                assertThat(childNames).doesNotHaveDuplicates();
            });
        }

        @Test
        @DisplayName("when defaults() is called - then Travel is present both as a group and as a child of Insurance")
        void whenDefaultsIsCalled_thenTravelIsPresentAsGroupAndAsChildOfInsurance() {
            List<Category> groups = Category.defaults();

            assertThat(groups).extracting(Category::name).contains("Travel");

            Category insurance = groupNamed(groups, "Insurance");
            assertThat(insurance.children()).extracting(Category::name).contains("Travel");
        }

        @Test
        @DisplayName(
                "when defaults() is called - then the tree is exactly two levels, no child of a group carries children of its own")
        void whenDefaultsIsCalled_thenTreeIsExactlyTwoLevels() {
            List<Category> groups = Category.defaults();
            assertThat(groups).hasSize(20);

            assertThat(groups).allSatisfy(group -> assertThat(group.children())
                    .allSatisfy(child -> assertThat(child.children()).isEmpty()));
        }

        private Category groupNamed(List<Category> groups, String name) {
            return groups.stream()
                    .filter(group -> group.name().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no group named " + name));
        }
    }
}
