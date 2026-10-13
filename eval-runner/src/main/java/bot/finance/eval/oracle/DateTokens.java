package bot.finance.eval.oracle;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Resolves the date tokens a case's MCP argument constraints may name, from the UTC date captured before filing. */
public final class DateTokens {

    private DateTokens() {}

    public static String resolve(String value, LocalDate runDate) {
        LocalDate previousMonday =
                runDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        return switch (value) {
            case "run-date" -> runDate.toString();
            case "run-date-minus-1-day" -> runDate.minusDays(1).toString();
            case "previous-week-monday" -> previousMonday.toString();
            case "previous-week-sunday" -> previousMonday.plusDays(6).toString();
            case "current-month-first-day" -> runDate.withDayOfMonth(1).toString();
            case "most-recent-friday" ->
                runDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY)).toString();
            case "current-year-october-10" ->
                LocalDate.of(runDate.getYear(), 10, 10).toString();
            case "current-year-october-12" ->
                LocalDate.of(runDate.getYear(), 10, 12).toString();
            default -> value;
        };
    }

    public static boolean isToken(String value) {
        return !resolve(value, LocalDate.of(2000, 1, 1)).equals(value);
    }
}
