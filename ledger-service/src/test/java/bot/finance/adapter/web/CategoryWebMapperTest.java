package bot.finance.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.api.model.ListCategories200ResponseInner;
import bot.finance.api.model.ListGroupings200ResponseInner;
import bot.finance.application.dto.CategoryEntry;
import bot.finance.application.dto.GroupingEntry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CategoryWebMapperTest {

    @Nested
    @DisplayName("mapping stored category entries onto the list-categories response")
    class ToCategories {

        @Test
        @DisplayName("when two category entries under different groupings are given - then each response carries "
                + "its id, name, grouping id and grouping name, in the order given")
        void whenTwoEntriesUnderDifferentGroupings_thenEachResponseCarriesItsFieldsInOrder() {
            CategoryEntry first = new CategoryEntry(1L, "Groceries", 10L, "Food");
            CategoryEntry second = new CategoryEntry(2L, "Transit", 20L, "Travel");

            List<ListCategories200ResponseInner> responses = CategoryWebMapper.toCategories(List.of(first, second));

            assertThat(responses).hasSize(2);
            ListCategories200ResponseInner firstResponse = responses.get(0);
            assertThat(firstResponse.getId()).isEqualTo(1L);
            assertThat(firstResponse.getName()).isEqualTo("Groceries");
            assertThat(firstResponse.getGroupingId()).isEqualTo(10L);
            assertThat(firstResponse.getGroupingName()).isEqualTo("Food");
            ListCategories200ResponseInner secondResponse = responses.get(1);
            assertThat(secondResponse.getId()).isEqualTo(2L);
            assertThat(secondResponse.getName()).isEqualTo("Transit");
            assertThat(secondResponse.getGroupingId()).isEqualTo(20L);
            assertThat(secondResponse.getGroupingName()).isEqualTo("Travel");
        }

        @Test
        @DisplayName("when an empty list is given - then an empty list is answered rather than null")
        void whenEmptyListIsGiven_thenAnEmptyListIsAnsweredRatherThanNull() {
            List<ListCategories200ResponseInner> responses = CategoryWebMapper.toCategories(List.of());

            assertThat(responses).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("mapping stored grouping entries onto the list-groupings response")
    class ToGroupings {

        @Test
        @DisplayName("when two grouping entries are given - then each response carries its id and name, in the "
                + "order given")
        void whenTwoGroupingEntriesAreGiven_thenEachResponseCarriesItsIdAndNameInOrder() {
            GroupingEntry first = new GroupingEntry(10L, "Food");
            GroupingEntry second = new GroupingEntry(20L, "Travel");

            List<ListGroupings200ResponseInner> responses = CategoryWebMapper.toGroupings(List.of(first, second));

            assertThat(responses).hasSize(2);
            ListGroupings200ResponseInner firstResponse = responses.get(0);
            assertThat(firstResponse.getId()).isEqualTo(10L);
            assertThat(firstResponse.getName()).isEqualTo("Food");
            ListGroupings200ResponseInner secondResponse = responses.get(1);
            assertThat(secondResponse.getId()).isEqualTo(20L);
            assertThat(secondResponse.getName()).isEqualTo("Travel");
        }
    }
}
