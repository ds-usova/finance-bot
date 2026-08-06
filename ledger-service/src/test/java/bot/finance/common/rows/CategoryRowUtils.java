package bot.finance.common.rows;

import bot.finance.adapter.persistence.CategoryEntity;
import java.util.List;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

public class CategoryRowUtils {

    private CategoryRowUtils() {}

    public static List<CategoryEntity> categoryRowsFor(JdbcAggregateTemplate jdbcAggregateTemplate, long userId) {
        return jdbcAggregateTemplate.findAll(CategoryEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }

    public static long storedGroupingId(JdbcAggregateTemplate jdbcAggregateTemplate, long userId, String name) {
        return jdbcAggregateTemplate
                .insert(new CategoryEntity(null, userId, null, name))
                .id();
    }

    public static long storedCategoryId(
            JdbcAggregateTemplate jdbcAggregateTemplate, long userId, long parentId, String name) {
        return jdbcAggregateTemplate
                .insert(new CategoryEntity(null, userId, parentId, name))
                .id();
    }
}
