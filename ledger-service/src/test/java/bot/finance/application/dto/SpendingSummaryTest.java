package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import bot.finance.domain.value.SpendingPeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SpendingSummaryTest {

    private static final SpendingPeriod PERIOD = new SpendingPeriod(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5));

    @Nested
    @DisplayName("constructing a spending summary")
    class SpendingSummaryConstructor {

        @Test
        @DisplayName("when totals are handed in out of currency-code order - "
                + "then totals() reads back ordered by currency code")
        void whenTotalsAreHandedInOutOfCurrencyCodeOrder_thenTotalsReadsBackOrderedByCurrencyCode() {
            CurrencyTotal usd = new CurrencyTotal(new Money(1000, new CurrencyCode("USD")), 1);
            CurrencyTotal eur = new CurrencyTotal(new Money(500, new CurrencyCode("EUR")), 2);

            SpendingSummary summary = new SpendingSummary(PERIOD, List.of(usd, eur));

            assertThat(summary.totals()).containsExactly(eur, usd);
        }

        @Test
        @DisplayName("when a mutable totals list handed to the constructor is modified afterwards - "
                + "then totals() is unchanged and unmodifiable")
        void whenMutableTotalsListIsModifiedAfterConstruction_thenTotalsIsUnchangedAndUnmodifiable() {
            CurrencyTotal eur = new CurrencyTotal(new Money(500, new CurrencyCode("EUR")), 2);
            List<CurrencyTotal> mutableTotals = new ArrayList<>(List.of(eur));

            SpendingSummary summary = new SpendingSummary(PERIOD, mutableTotals);
            mutableTotals.add(new CurrencyTotal(new Money(1000, new CurrencyCode("USD")), 1));

            assertThat(summary.totals()).containsExactly(eur);
            assertThatThrownBy(() -> summary.totals().add(eur)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("when the totals list is empty - then the summary is built and totals() is empty")
        void whenTotalsListIsEmpty_thenTheSummaryIsBuiltAndTotalsIsEmpty() {
            SpendingSummary summary = new SpendingSummary(PERIOD, List.of());

            assertThat(summary.totals()).isEmpty();
        }
    }
}
