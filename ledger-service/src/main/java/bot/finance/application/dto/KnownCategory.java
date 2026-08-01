package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidExtractionRequestException;

public record KnownCategory(String name, String parentName) {

    public KnownCategory {
        if (name == null || name.isBlank()) {
            throw new InvalidExtractionRequestException("Known category name must not be null or blank");
        }
        if (parentName == null || parentName.isBlank()) {
            throw new InvalidExtractionRequestException("Known category parent name must not be null or blank");
        }
    }
}
