package bot.finance.domain.value;

import java.time.LocalDate;

public record SpendingPeriod(LocalDate from, LocalDate to) {

    public SpendingPeriod {
        // TODO(RU01): refuse an absent from or to, and a to before the from, with
        // InvalidSpendingPeriodException
    }

    public static SpendingPeriod of(String from, String to) {
        // parses each written date as an ISO-8601 YYYY-MM-DD value, refusing an absent, blank or
        // unparseable one, and refuses a period whose last day precedes its first
        return null;
    }
}
