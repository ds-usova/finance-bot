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
                .map(day -> new DayTotal(day.getKey(), toAmounts(day.getValue())))
                .toList();

        return new ExpensePage(items, limit, offset, total, dayTotals);
    }

    private static List<Money> toAmounts(Map<CurrencyCode, Long> minorUnitsByCurrency) {
        return minorUnitsByCurrency.entrySet().stream()
                .map(currencyTotal -> new Money(currencyTotal.getValue(), currencyTotal.getKey()))
                .toList();
    }
}
