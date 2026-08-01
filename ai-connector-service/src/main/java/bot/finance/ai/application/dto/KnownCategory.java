package bot.finance.ai.application.dto;

import bot.finance.ai.domain.exception.InvalidValueException;

public record KnownCategory(String name, String parentName) {

    public KnownCategory {
        if (name == null || name.isBlank()) {
            throw new InvalidValueException("Known category name must not be null or blank");
        }
        if (parentName == null || parentName.isBlank()) {
            throw new InvalidValueException("Known category parent name must not be null or blank");
        }
    }

    public String label() {
        return parentName + " > " + name;
    }
}
