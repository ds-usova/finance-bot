package bot.finance.domain.value;

import bot.finance.domain.exception.InvalidSpendingPeriodException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public record SpendingPeriod(LocalDate from, LocalDate to) {

    public SpendingPeriod {
        if (from == null) {
            throw new InvalidSpendingPeriodException("First day must not be null");
        }
        if (to == null) {
            throw new InvalidSpendingPeriodException("Last day must not be null");
        }
        if (to.isBefore(from)) {
            throw new InvalidSpendingPeriodException("Period ends before it starts");
        }
    }

    public static SpendingPeriod of(String from, String to) {
        LocalDate parsedFrom = parseDay(from, "first day");
        LocalDate parsedTo = parseDay(to, "last day");

        return new SpendingPeriod(parsedFrom, parsedTo);
    }

    private static LocalDate parseDay(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidSpendingPeriodException(label + " must not be blank");
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidSpendingPeriodException(value + " is not a valid ISO-8601 date for the " + label);
        }
    }
}
