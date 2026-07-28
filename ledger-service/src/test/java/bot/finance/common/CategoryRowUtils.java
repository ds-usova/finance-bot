package bot.finance.common;

import bot.finance.adapter.persistence.CategoryEntity;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

import java.util.List;

public class CategoryRowUtils {

    private CategoryRowUtils() {
    }

    public static List<CategoryEntity> categoryRowsFor(JdbcAggregateTemplate jdbcAggregateTemplate, long userId) {
        return jdbcAggregateTemplate.findAll(CategoryEntity.class).stream()
                .filter(row -> row.userId() == userId)
                .toList();
    }

}
