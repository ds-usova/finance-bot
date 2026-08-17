package bot.finance.ai.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.ExampleOutcome;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RecordedExpenseEntityTest {

    @Nested
    @DisplayName("toExampleExpense()")
    class ToExampleExpense {

        @Test
        @DisplayName("when the currency has two minor digits - then the amount scales into the example accordingly")
        void whenCurrencyHasTwoMinorDigits_thenAmountScalesIntoExampleAccordingly() {
            RecordedExpenseEntity entity = expense(1234, "EUR", "Groceries", "Food", "ACCEPTED");

            ExampleExpense example = entity.toExampleExpense();

            assertThat(example.amount()).isEqualTo("12.34");
            assertThat(example.currency().code()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("when the currency has no minor unit - then the amount answers with no decimal places")
        void whenCurrencyHasNoMinorUnit_thenAmountAnswersWithNoDecimalPlaces() {
            RecordedExpenseEntity entity = expense(1234, "JPY", "Groceries", "Food", "ACCEPTED");

            ExampleExpense example = entity.toExampleExpense();

            assertThat(example.amount()).isEqualTo("1234");
        }

        @Test
        @DisplayName("when the category name is absent - then the example's category name is empty")
        void whenCategoryNameIsAbsent_thenExampleCategoryNameIsEmpty() {
            RecordedExpenseEntity entity = expense(1234, "EUR", null, "Food", "ACCEPTED");

            ExampleExpense example = entity.toExampleExpense();

            assertThat(example.categoryName()).isEqualTo(Optional.empty());
        }

        @Test
        @DisplayName("when the grouping name is absent - then the example's grouping name is empty")
        void whenGroupingNameIsAbsent_thenExampleGroupingNameIsEmpty() {
            RecordedExpenseEntity entity = expense(1234, "EUR", "Groceries", null, "ACCEPTED");

            ExampleExpense example = entity.toExampleExpense();

            assertThat(example.groupingName()).isEqualTo(Optional.empty());
        }

        @Test
        @DisplayName("when the status is ACCEPTED - then the example's outcome is ACCEPTED")
        void whenStatusIsAccepted_thenExampleOutcomeIsAccepted() {
            RecordedExpenseEntity entity = expense(1234, "EUR", "Groceries", "Food", "ACCEPTED");

            ExampleExpense example = entity.toExampleExpense();

            assertThat(example.outcome()).isEqualTo(ExampleOutcome.ACCEPTED);
        }

        @Test
        @DisplayName("when the status is DISCARDED - then the example's outcome is DISCARDED")
        void whenStatusIsDiscarded_thenExampleOutcomeIsDiscarded() {
            RecordedExpenseEntity entity = expense(1234, "EUR", "Groceries", "Food", "DISCARDED");

            ExampleExpense example = entity.toExampleExpense();

            assertThat(example.outcome()).isEqualTo(ExampleOutcome.DISCARDED);
        }

        private RecordedExpenseEntity expense(
                long amountMinorUnits, String currencyCode, String categoryName, String groupingName, String status) {
            return new RecordedExpenseEntity(
                    1L,
                    2L,
                    3L,
                    null,
                    null,
                    "Weekly groceries",
                    "Market",
                    amountMinorUnits,
                    currencyCode,
                    4L,
                    categoryName,
                    groupingName,
                    status,
                    null,
                    Instant.parse("2026-08-17T00:00:00Z"));
        }
    }
}
