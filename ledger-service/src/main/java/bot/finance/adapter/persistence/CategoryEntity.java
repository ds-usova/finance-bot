package bot.finance.adapter.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import bot.finance.domain.value.Category;

@Table("category")
public record CategoryEntity(@Id Long id, Long userId, Long parentId, String name) {

    public static CategoryEntity root(long userId, Category category) {
        return new CategoryEntity(null, userId, null, category.name());
    }

    public static CategoryEntity child(long userId, long parentId, Category category) {
        return new CategoryEntity(null, userId, parentId, category.name());
    }

}
