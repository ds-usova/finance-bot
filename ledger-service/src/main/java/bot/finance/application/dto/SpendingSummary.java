package bot.finance.application.dto;

import bot.finance.domain.value.SpendingPeriod;
import java.util.List;

public record SpendingSummary(SpendingPeriod period, List<CurrencyTotal> totals) {

    public SpendingSummary {
        // TODO(RU05): copy totals and order it by currency code; empty when the period holds nothing
    }
}
