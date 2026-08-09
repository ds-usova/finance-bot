package bot.finance.application.dto;

import bot.finance.domain.value.Money;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record DayTotal(LocalDate day, List<Money> amounts) {

    public DayTotal {
        amounts = amounts.stream()
                .sorted(Comparator.comparing(amount -> amount.currencyCode().code()))
                .toList();
    }
}
