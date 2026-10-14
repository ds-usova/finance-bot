package bot.finance.ai.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import bot.finance.ai.application.port.TurnMeters;
import bot.finance.ai.domain.value.CatalogueEntry;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.tool.execution.ToolExecutionException;

class CategoryLookupToolCallbackTest {

    private static final String NO_GROUPING_TEXT = "list categories command has no grouping name";

    private final TurnMeters turnMeters = mock(TurnMeters.class);

    private CategoryLookupToolCallback callbackOver(CatalogueEntry... entries) {
        return new CategoryLookupToolCallback(List.of(entries), turnMeters);
    }

    private static CatalogueEntry food() {
        return new CatalogueEntry("Food", List.of("Groceries", "Restaurants"));
    }

    private void assertRefused(CategoryLookupToolCallback callback, String arguments, String message) {
        assertThatThrownBy(() -> callback.call(arguments))
                .isInstanceOf(ToolExecutionException.class)
                .cause()
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessage(message);
        verify(turnMeters).countCategoryLookup(false);
        verify(turnMeters, never()).countCategoryLookup(true);
    }

    @Nested
    @DisplayName("call()")
    class Call {

        @Test
        @DisplayName("when the grouping is in the catalogue - then its categories are answered and the lookup counted")
        void whenGroupingStored_thenAnswersItsCategories() {
            String answer = callbackOver(food()).call("""
                    {"grouping":"Food"}""");

            assertThat(answer)
                    .isEqualTo("""
                    {"grouping":"Food","categories":["Groceries","Restaurants"]}""");
            verify(turnMeters).countCategoryLookup(true);
            verify(turnMeters, never()).countCategoryLookup(false);
        }

        @Test
        @DisplayName("when the name is only a category - then the call is refused as a category, not a grouping")
        void whenNameIsOnlyACategory_thenRefused() {
            assertRefused(
                    callbackOver(food()),
                    """
                    {"grouping":"Groceries"}""",
                    "Groceries is a category, not a grouping");
        }

        @Test
        @DisplayName("when the name is stored nowhere - then the call is refused as not stored for this user")
        void whenNameIsNotStored_thenRefused() {
            assertRefused(
                    callbackOver(food()),
                    """
                    {"grouping":"Travel"}""",
                    "no grouping named Travel is stored for this user");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    """
                    {"grouping":"   "}""",
                    """
                    {"grouping":""}""",
                    """
                    {"grouping":null}""",
                    "{}"
                })
        @DisplayName("when the grouping is blank, null or absent - then the call is refused as having no grouping name")
        void whenGroupingBlankOrAbsent_thenRefused(String arguments) {
            assertRefused(callbackOver(food()), arguments, NO_GROUPING_TEXT);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "not json",
                    "",
                    """
                    {"grouping":5}""",
                    """
                    {"grouping":["Food"]}""",
                    """
                    {"grouping":{"name":"Food"}}"""
                })
        @DisplayName("when the arguments cannot be read - then the call is refused as having no grouping name")
        void whenArgumentsUnreadable_thenRefused(String arguments) {
            assertRefused(callbackOver(food()), arguments, NO_GROUPING_TEXT);
        }

        @Test
        @DisplayName("when the name differs from a stored grouping only in case - then it is refused as not stored")
        void whenNameDiffersInCase_thenRefusedAsNotStored() {
            assertRefused(
                    callbackOver(food()),
                    """
                    {"grouping":"food"}""",
                    "no grouping named food is stored for this user");
        }

        @Test
        @DisplayName("when the name is both a grouping and its own category - then it answers as the grouping")
        void whenNameIsGroupingAndCategory_thenAnswersAsGrouping() {
            String answer = callbackOver(new CatalogueEntry("Other", List.of("Other")))
                    .call("""
                    {"grouping":"Other"}""");

            assertThat(answer).isEqualTo("""
                    {"grouping":"Other","categories":["Other"]}""");
            verify(turnMeters).countCategoryLookup(true);
        }

        @Test
        @DisplayName("when the same grouping is looked up twice - then both answers are alike and two lookups counted")
        void whenSameGroupingLookedUpTwice_thenAnswersAlike() {
            CategoryLookupToolCallback callback = callbackOver(food());

            String first = callback.call("""
                    {"grouping":"Food"}""");
            String second = callback.call("""
                    {"grouping":"Food"}""");

            assertThat(first).isNotEmpty().isEqualTo(second);
            verify(turnMeters, times(2)).countCategoryLookup(true);
            verify(turnMeters, never()).countCategoryLookup(false);
        }
    }
}
