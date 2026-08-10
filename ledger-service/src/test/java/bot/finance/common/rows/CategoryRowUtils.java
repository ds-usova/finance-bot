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

    /** The id of the first category the user has under a grouping, as the initial tree gives them. */
    public static long firstLeafCategoryId(JdbcAggregateTemplate jdbcAggregateTemplate, long userId) {
        return categoryRowsFor(jdbcAggregateTemplate, userId).stream()
                .filter(row -> row.parentId() != null)
                .findFirst()
                .map(CategoryEntity::id)
                .orElseThrow();
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
