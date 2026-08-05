package bot.finance.adapter.persistence;

import bot.finance.application.dto.CurrencyTotal;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.Money;

public record CurrencyTotalProjection(String currencyCode, long totalMinorUnits, int expenseCount) {

    public CurrencyTotal toCurrencyTotal() {
        return new CurrencyTotal(new Money(totalMinorUnits, CurrencyCode.of(currencyCode)), expenseCount);
    }
}
