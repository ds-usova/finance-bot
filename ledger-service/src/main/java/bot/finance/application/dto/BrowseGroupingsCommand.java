package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;

public record BrowseGroupingsCommand(AuthenticatedUserId userId) {

    public BrowseGroupingsCommand {
        // TODO: refuse a null userId
    }
}
