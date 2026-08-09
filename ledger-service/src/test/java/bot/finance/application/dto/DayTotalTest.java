package bot.finance.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DayTotalTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 1);

    @Nested
    @DisplayName("constructing a day total")
    class DayTotalConstructor {

        @Test
        @DisplayName("when amounts are handed in out of currency-code order - "
                + "then amounts() reads back ordered by currency code")
        void whenAmountsAreHandedInOutOfCurrencyCodeOrder_thenAmountsReadsBackOrderedByCurrencyCode() {
            Money jpy = new Money(900L, CurrencyCode.of("JPY"));
            Money eur = new Money(1250L, CurrencyCode.of("EUR"));

            DayTotal dayTotal = new DayTotal(DAY, List.of(jpy, eur));

            assertThat(dayTotal.amounts()).containsExactly(eur, jpy);
        }

        @Test
        @DisplayName("when the mutable list a day total was built from is modified - then amounts() is unchanged")
        void whenMutableAmountsListIsModifiedAfterConstruction_thenAmountsIsUnchanged() {
            Money eur = new Money(500L, CurrencyCode.of("EUR"));
            List<Money> mutableAmounts = new ArrayList<>(List.of(eur));

            DayTotal dayTotal = new DayTotal(DAY, mutableAmounts);
            mutableAmounts.add(new Money(1000L, CurrencyCode.of("USD")));

            assertThat(dayTotal.amounts()).containsExactly(eur);
        }

        @Test
        @DisplayName("when a day total is built from an amounts list - then amounts() is unmodifiable")
        void whenDayTotalIsBuiltFromAnAmountsList_thenAmountsIsUnmodifiable() {
            Money eur = new Money(500L, CurrencyCode.of("EUR"));

            DayTotal dayTotal = new DayTotal(DAY, List.of(eur));

            assertThatThrownBy(() -> dayTotal.amounts().add(eur)).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("when the amounts list is empty - then the day total is built and amounts() is empty")
        void whenAmountsListIsEmpty_thenTheDayTotalIsBuiltAndAmountsIsEmpty() {
            DayTotal dayTotal = new DayTotal(DAY, List.of());

            assertThat(dayTotal.amounts()).isEmpty();
        }
    }
}
