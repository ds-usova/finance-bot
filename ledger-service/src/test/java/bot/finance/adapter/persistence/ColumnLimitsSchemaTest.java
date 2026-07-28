package bot.finance.adapter.persistence;

import bot.finance.common.PersistenceAdapterTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@PersistenceAdapterTest
class ColumnLimitsSchemaTest {

    private static final String COLUMN_WIDTH_QUERY = """
            SELECT character_maximum_length
            FROM information_schema.columns
            WHERE table_schema = current_schema()
              AND table_name = ?
              AND column_name = ?
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Nested
    @DisplayName("app_user.external_id column width")
    class ExternalId {

        @Test
        @DisplayName("when the migrated schema's character_maximum_length is read for app_user.external_id - then it equals ColumnLimits.EXTERNAL_ID")
        void whenMigratedColumnWidthRead_thenEqualsExternalIdConstant() {
            Integer characterMaximumLength = jdbcTemplate.queryForObject(
                    COLUMN_WIDTH_QUERY, Integer.class, "app_user", "external_id");

            assertThat(characterMaximumLength).isEqualTo(ColumnLimits.EXTERNAL_ID);
        }

    }

    @Nested
    @DisplayName("category.name column width")
    class CategoryName {

        @Test
        @DisplayName("when the migrated schema's character_maximum_length is read for category.name - then it equals ColumnLimits.CATEGORY_NAME")
        void whenMigratedColumnWidthRead_thenEqualsCategoryNameConstant() {
            Integer characterMaximumLength = jdbcTemplate.queryForObject(
                    COLUMN_WIDTH_QUERY, Integer.class, "category", "name");

            assertThat(characterMaximumLength).isEqualTo(ColumnLimits.CATEGORY_NAME);
        }

    }

}
