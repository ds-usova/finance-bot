package bot.finance.ai.domain.value;

import bot.finance.ai.domain.exception.InvalidValueException;

public record CategoryChoice(String grouping, String category) {

    public CategoryChoice {
        if (grouping == null || grouping.isBlank()) {
            throw new InvalidValueException("Grouping must not be null or blank");
        }
        if (category == null || category.isBlank()) {
            throw new InvalidValueException("Category must not be null or blank");
        }
    }
}
