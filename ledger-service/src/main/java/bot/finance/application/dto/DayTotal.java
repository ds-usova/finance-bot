package bot.finance.application.dto;

import bot.finance.domain.value.Money;
import java.time.LocalDate;
import java.util.List;

public record DayTotal(LocalDate day, List<Money> amounts) {

    public DayTotal {
        // orders amounts by ISO currency code and copies the list, the way SpendingSummary does
    }
}
