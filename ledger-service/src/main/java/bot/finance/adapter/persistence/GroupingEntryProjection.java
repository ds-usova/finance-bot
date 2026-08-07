package bot.finance.adapter.persistence;

import bot.finance.application.dto.GroupingEntry;

public record GroupingEntryProjection(long id, String name) {

    public GroupingEntry toGroupingEntry() {
        return new GroupingEntry(id, name);
    }
}
