package bot.finance.adapter.web;

import bot.finance.api.model.ListCategories200ResponseInner;
import bot.finance.api.model.ListGroupings200ResponseInner;
import bot.finance.application.dto.CategoryEntry;
import bot.finance.application.dto.GroupingEntry;
import java.util.List;

public final class CategoryWebMapper {

    private CategoryWebMapper() {}

    public static List<ListCategories200ResponseInner> toCategories(List<CategoryEntry> entries) {
        // TODO: map each CategoryEntry into a ListCategories200ResponseInner, preserving order
        return List.of();
    }

    public static List<ListGroupings200ResponseInner> toGroupings(List<GroupingEntry> entries) {
        // TODO: map each GroupingEntry into a ListGroupings200ResponseInner, preserving order
        return List.of();
    }
}
