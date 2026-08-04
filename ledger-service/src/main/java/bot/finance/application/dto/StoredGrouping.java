package bot.finance.application.dto;

import bot.finance.domain.exception.InvalidGroupingException;

public record StoredGrouping(long id, String name) {

    public StoredGrouping {
        if (id <= 0) {
            throw new InvalidGroupingException("stored grouping has a non-positive id");
        }
        if (name == null || name.isBlank()) {
            throw new InvalidGroupingException("stored grouping has no name");
        }
    }
}
