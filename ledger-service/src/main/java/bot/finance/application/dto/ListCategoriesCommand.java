package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.value.AuthenticatedUserId;

public record ListCategoriesCommand(AuthenticatedUserId userId, String groupingName) {

    public ListCategoriesCommand {
        if (userId == null) {
            throw new InvalidGroupingException("list categories command has no userId");
        }
        if (groupingName == null || groupingName.isBlank()) {
            throw new InvalidGroupingException("list categories command has no grouping name");
        }
    }
}
