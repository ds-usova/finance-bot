package bot.finance.adapter.persistence;

import bot.finance.application.dto.CategoryEntry;

public record CategoryEntryProjection(long id, String name, long groupingId, String groupingName) {

    public CategoryEntry toCategoryEntry() {
        return new CategoryEntry(id, name, groupingId, groupingName);
    }
}
