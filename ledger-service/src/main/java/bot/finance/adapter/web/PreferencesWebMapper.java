package bot.finance.adapter.web;

import bot.finance.api.model.ReadPreferences200Response;
import bot.finance.api.model.ReplacePreferencesRequest;
import bot.finance.application.dto.Preferences;
import bot.finance.application.dto.ReplacePreferencesCommand;
import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;

public final class PreferencesWebMapper {

    private PreferencesWebMapper() {}

    public static ReplacePreferencesCommand toReplacePreferencesCommand(
            ReplacePreferencesRequest request, AuthenticatedUserId userId) {
        CurrencyCode defaultCurrency = CurrencyCode.of(request.getDefaultCurrency());
        if (!defaultCurrency.recordsAmounts()) {
            throw new InvalidMoneyException(
                    "Currency code " + defaultCurrency.code() + ": no amount can be recorded in it");
        }

        return new ReplacePreferencesCommand(userId, defaultCurrency);
    }

    public static ReadPreferences200Response toResponse(Preferences preferences) {
        return new ReadPreferences200Response()
                .defaultCurrency(
                        preferences.defaultCurrency().map(CurrencyCode::code).orElse(null));
    }
}
