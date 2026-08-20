package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;
import java.util.List;
import java.util.Objects;

public record MessageExample(String text, List<ExampleExpense> expenses) {

    public MessageExample {
        if (text == null || text.isBlank()) {
            throw new InvalidValueException("Text must not be null or blank");
        }
        if (expenses == null || expenses.isEmpty()) {
            throw new InvalidValueException("Expenses must not be null or empty");
        }
        if (expenses.stream().anyMatch(Objects::isNull)) {
            throw new InvalidValueException("Expenses must not hold a null element");
        }
        expenses = List.copyOf(expenses);
    }
}
