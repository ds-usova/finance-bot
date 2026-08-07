package bot.finance.application.dto;

import bot.finance.domain.value.AuthenticatedUserId;

public record BrowseCategoriesCommand(AuthenticatedUserId userId, Long groupingId) {

    public BrowseCategoriesCommand {
        // TODO: refuse a null userId; groupingId is optional
    }
}
