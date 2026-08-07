package bot.finance.adapter.web;

import bot.finance.api.model.ListCategories200ResponseInner;
import bot.finance.api.model.ListGroupings200ResponseInner;
import bot.finance.application.dto.CategoryEntry;
import bot.finance.application.dto.GroupingEntry;
import java.util.List;

public final class CategoryWebMapper {

    private CategoryWebMapper() {}

    public static List<ListCategories200ResponseInner> toCategories(List<CategoryEntry> entries) {
        return entries.stream().map(CategoryWebMapper::toCategory).toList();
    }

    public static List<ListGroupings200ResponseInner> toGroupings(List<GroupingEntry> entries) {
        return entries.stream().map(CategoryWebMapper::toGrouping).toList();
    }

    private static ListCategories200ResponseInner toCategory(CategoryEntry entry) {
        return new ListCategories200ResponseInner(
                entry.id(), entry.name(), entry.groupingId(), entry.groupingName());
    }

    private static ListGroupings200ResponseInner toGrouping(GroupingEntry entry) {
        return new ListGroupings200ResponseInner(entry.id(), entry.name());
    }
}
