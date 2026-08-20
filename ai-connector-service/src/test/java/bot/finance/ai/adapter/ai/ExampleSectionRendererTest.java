package bot.finance.ai.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.ExampleOutcome;
import bot.finance.ai.domain.value.MessageExample;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExampleSectionRendererTest {

    private static final CurrencyCode EUR = CurrencyCode.of("EUR");
    private static final CurrencyCode USD = CurrencyCode.of("USD");

    private final ExampleSectionRenderer renderer = new ExampleSectionRenderer();

    private static ExampleExpense expense(
            String description,
            String amount,
            CurrencyCode currency,
            Optional<String> categoryName,
            Optional<String> groupingName,
            ExampleOutcome outcome) {
        return new ExampleExpense(description, amount, currency, categoryName, groupingName, outcome);
    }

    @Nested
    @DisplayName("render()")
    class Render {

        @Test
        @DisplayName("when the retrieval is absent - then it answers the empty string")
        void whenRetrievalIsAbsent_thenAnswersEmptyString() {
            String section = renderer.render(Optional.empty());

            assertThat(section).isEmpty();
        }

        @Test
        @DisplayName("when the retrieval is present but holds no example - then the section reads none")
        void whenRetrievalHoldsNoExample_thenSectionReadsNone() {
            String section = renderer.render(Optional.of(List.of()));

            assertThat(section).isEqualTo("none");
        }

        @Test
        @DisplayName("when one example names two accepted expenses - then the message is quoted once with a line "
                + "per expense")
        void whenOneExampleNamesTwoAcceptedExpenses_thenMessageIsQuotedOnceWithLinePerExpense() {
            MessageExample example = new MessageExample(
                    "spent 15 on lunch and 3.50 coffee",
                    List.of(
                            expense(
                                    "lunch",
                                    "15.00",
                                    EUR,
                                    Optional.of("Restaurants"),
                                    Optional.of("Dining"),
                                    ExampleOutcome.ACCEPTED),
                            expense(
                                    "coffee",
                                    "3.50",
                                    EUR,
                                    Optional.of("Coffee"),
                                    Optional.of("Dining"),
                                    ExampleOutcome.ACCEPTED)));

            String section = renderer.render(Optional.of(List.of(example)));

            String expected =
                    """
                    - "spent 15 on lunch and 3.50 coffee"
                      - lunch, 15.00 EUR — Restaurants (Dining) — accepted
                      - coffee, 3.50 EUR — Coffee (Dining) — accepted""";
            assertThat(section).isEqualTo(expected);
        }

        @Test
        @DisplayName("when an example holds a discarded expense beside an accepted one - then each line names its "
                + "own outcome")
        void whenExampleHoldsDiscardedExpenseBesideAcceptedOne_thenEachLineNamesItsOwnOutcome() {
            MessageExample example = new MessageExample(
                    "spent 15 on lunch and 3.50 coffee",
                    List.of(
                            expense(
                                    "lunch",
                                    "15.00",
                                    EUR,
                                    Optional.of("Restaurants"),
                                    Optional.of("Dining"),
                                    ExampleOutcome.ACCEPTED),
                            expense(
                                    "coffee",
                                    "3.50",
                                    EUR,
                                    Optional.of("Coffee"),
                                    Optional.of("Dining"),
                                    ExampleOutcome.DISCARDED)));

            String section = renderer.render(Optional.of(List.of(example)));

            String expected =
                    """
                    - "spent 15 on lunch and 3.50 coffee"
                      - lunch, 15.00 EUR — Restaurants (Dining) — accepted
                      - coffee, 3.50 EUR — Coffee (Dining) — discarded""";
            assertThat(section).isEqualTo(expected);
        }

        @Test
        @DisplayName("when expenses lack a category or a grouping name - then each line drops what it lacks and "
                + "keeps the rest")
        void whenExpensesLackCategoryOrGroupingName_thenEachLineDropsWhatItLacksAndKeepsRest() {
            MessageExample example = new MessageExample(
                    "bought a book and a gift",
                    List.of(
                            expense(
                                    "book",
                                    "9.99",
                                    USD,
                                    Optional.empty(),
                                    Optional.of("Shopping"),
                                    ExampleOutcome.ACCEPTED),
                            expense(
                                    "gift",
                                    "20.00",
                                    USD,
                                    Optional.of("Gifts"),
                                    Optional.empty(),
                                    ExampleOutcome.ACCEPTED)));

            String section = renderer.render(Optional.of(List.of(example)));

            assertThat(section).contains("book, 9.99 USD").contains("Shopping").contains("accepted");
            assertThat(section).contains("gift, 20.00 USD").contains("Gifts").contains("accepted");
            assertThat(section).doesNotContain("null").doesNotContain("()");
        }

        @Test
        @DisplayName("when two examples are given - then both appear, in the order given")
        void whenTwoExamplesGiven_thenBothAppearInOrderGiven() {
            MessageExample first = new MessageExample(
                    "first message",
                    List.of(expense(
                            "lunch",
                            "15.00",
                            EUR,
                            Optional.of("Restaurants"),
                            Optional.of("Dining"),
                            ExampleOutcome.ACCEPTED)));
            MessageExample second = new MessageExample(
                    "second message",
                    List.of(expense(
                            "coffee",
                            "3.50",
                            EUR,
                            Optional.of("Coffee"),
                            Optional.of("Dining"),
                            ExampleOutcome.ACCEPTED)));

            String section = renderer.render(Optional.of(List.of(first, second)));

            assertThat(section).contains("first message").contains("second message");
            assertThat(section.indexOf("first message")).isLessThan(section.indexOf("second message"));
        }
    }
}
