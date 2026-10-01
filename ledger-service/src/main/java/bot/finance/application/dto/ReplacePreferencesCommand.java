package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;

public record ReplacePreferencesCommand(AuthenticatedUserId userId, CurrencyCode defaultCurrency) {

    public ReplacePreferencesCommand {
        if (userId == null) {
            throw new InvalidUserException("replace preferences command has no user id");
        }
        if (defaultCurrency == null) {
            throw new InvalidMoneyException("replace preferences command has no default currency");
        }
    }
}
