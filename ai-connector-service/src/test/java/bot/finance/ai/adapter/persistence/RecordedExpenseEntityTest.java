package bot.finance.ai.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.ExampleExpense;
import bot.finance.ai.domain.value.ExampleOutcome;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RecordedExpenseEntityTest {

    @Nested
    @DisplayName("toExampleExpense()")
    class ToExampleExpense {

        @Test
        @DisplayName("when the amount reads 4.50 and the currency is USD - then the example keeps 4.50 USD unscaled")
        void whenAmountReads450AndCurrencyIsUsd_thenExampleAmountIs450AndCurrencyIsUsd() {
            RecordedExpenseEntity entity = expense("4.50", "USD", "Groceries", "Food", "ACCEPTED");

            ExampleExpense example = entity.toExampleExpense();

            assertThat(example.amount()).isEqualTo("4.50");
            assertThat(example.currency()).isEqualTo(CurrencyCode.of("USD"));
        }

        @Test
        @DisplayName("when the amount reads 7200 and the currency is JPY - then the example's amount is 7200")
        void whenAmountReads7200AndCurrencyIsJpy_thenExampleAmountIs7200() {
            RecordedExpenseEntity entity = expense("7200", "JPY", "Groceries", "Food", "ACCEPTED");

            ExampleExpense example = entity.toExampleExpense();

            assertThat(example.amount()).isEqualTo("7200");
        }

        @Test
        @DisplayName("when the status is ACCEPTED - then the example's outcome is ACCEPTED")
        void whenStatusIsAccepted_thenExampleOutcomeIsAccepted() {
            RecordedExpenseEntity entity = expense("12.34", "EUR", "Groceries", "Food", "ACCEPTED");

            ExampleExpense example = entity.toExampleExpense();

            assertThat(example.outcome()).isEqualTo(ExampleOutcome.ACCEPTED);
        }

        @Test
        @DisplayName("when the status is DISCARDED - then the example's outcome is DISCARDED")
        void whenStatusIsDiscarded_thenExampleOutcomeIsDiscarded() {
            RecordedExpenseEntity entity = expense("12.34", "EUR", "Groceries", "Food", "DISCARDED");

            ExampleExpense example = entity.toExampleExpense();

            assertThat(example.outcome()).isEqualTo(ExampleOutcome.DISCARDED);
        }

        private RecordedExpenseEntity expense(
                String amount, String currencyCode, String categoryName, String groupingName, String status) {
            return new RecordedExpenseEntity(
                    1L,
                    2L,
                    3L,
                    4L,
                    "Weekly groceries",
                    "Market",
                    amount,
                    currencyCode,
                    5L,
                    categoryName,
                    6L,
                    groupingName,
                    status,
                    1_700_000_000_000L,
                    0L,
                    Instant.parse("2026-08-17T00:00:00Z"));
        }
    }
}
