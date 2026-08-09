package bot.finance.application.dto;

import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.ExpenseStatus;
import bot.finance.domain.value.Money;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ExpensePage(List<ExpenseEntry> items, int limit, int offset, long total, List<DayTotal> dayTotals) {

    public static ExpensePage of(List<ExpenseEntry> items, int limit, int offset, long total) {
        Map<LocalDate, Map<CurrencyCode, Long>> minorUnitsByDayAndCurrency = new LinkedHashMap<>();
        for (ExpenseEntry item : items) {
            if (item.status() != ExpenseStatus.RECORDED) {
                continue;
            }
            LocalDate day = item.createdAt().atZone(ZoneOffset.UTC).toLocalDate();
            minorUnitsByDayAndCurrency
                    .computeIfAbsent(day, ignored -> new LinkedHashMap<>())
                    .merge(item.money().currencyCode(), item.money().minorUnits(), Long::sum);
        }

        List<DayTotal> dayTotals = minorUnitsByDayAndCurrency.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder()))
                .map(entry -> new DayTotal(
                        entry.getKey(),
                        entry.getValue().entrySet().stream()
                                .map(currencyTotal -> new Money(currencyTotal.getValue(), currencyTotal.getKey()))
                                .toList()))
                .toList();

        return new ExpensePage(items, limit, offset, total, dayTotals);
    }
}
