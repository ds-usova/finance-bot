package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidValueException;
import bot.finance.domain.value.CurrencyCode;
import java.util.Optional;

public record Preferences(Optional<CurrencyCode> defaultCurrency) {

    public Preferences {
        if (defaultCurrency == null) {
            throw new InvalidValueException("preferences has no defaultCurrency optional");
        }
    }
}
