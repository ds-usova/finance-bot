package bot.finance.adapter.web;

import bot.finance.api.model.ReadPreferences200Response;
import bot.finance.api.model.ReplacePreferencesRequest;
import bot.finance.application.dto.Preferences;
import bot.finance.application.dto.ReplacePreferencesCommand;
import bot.finance.domain.value.AuthenticatedUserId;

public final class PreferencesWebMapper {

    private PreferencesWebMapper() {}

    public static ReplacePreferencesCommand toReplacePreferencesCommand(
            ReplacePreferencesRequest request, AuthenticatedUserId userId) {
        // upper-cases the request's code through CurrencyCode and pairs it with the caller, refusing a code ISO
        // 4217 does not know or one no amount can be recorded in before the command is built
        return null;
    }

    public static ReadPreferences200Response toResponse(Preferences preferences) {
        // renders the stored currency's upper-cased code, or null where none is set
        return null;
    }
}
