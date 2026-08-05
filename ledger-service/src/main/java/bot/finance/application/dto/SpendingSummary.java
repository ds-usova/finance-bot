package bot.finance.application.dto;

import bot.finance.domain.value.SpendingPeriod;
import java.util.Comparator;
import java.util.List;

public record SpendingSummary(SpendingPeriod period, List<CurrencyTotal> totals) {

    public SpendingSummary {
        totals = totals.stream()
                .sorted(Comparator.comparing(
                        total -> total.total().currencyCode().code()))
                .toList();
    }
}
