package bot.finance.adapter.persistence;

import bot.finance.application.dto.StoredCategory;
import bot.finance.domain.value.Category;
import bot.finance.domain.value.Grouping;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("category")
public record CategoryEntity(@Id Long id, Long userId, Long parentId, String name) {

    public static CategoryEntity grouping(long userId, Grouping grouping) {
        return new CategoryEntity(null, userId, null, grouping.name());
    }

    public static CategoryEntity category(long userId, long groupingId, Category category) {
        return new CategoryEntity(null, userId, groupingId, category.name());
    }

    public StoredCategory toStoredCategory() {
        return new StoredCategory(id, name);
    }
}
