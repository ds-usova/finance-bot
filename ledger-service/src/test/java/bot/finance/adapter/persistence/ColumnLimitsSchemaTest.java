package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.PersistenceAdapterTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PersistenceAdapterTest
class ColumnLimitsSchemaTest {

    private static final String COLUMN_WIDTH_QUERY =
            """
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
        @DisplayName(
                "when the migrated schema's character_maximum_length is read for app_user.external_id - then it equals ColumnLimits.EXTERNAL_ID")
        void whenMigratedColumnWidthRead_thenEqualsExternalIdConstant() {
            Integer characterMaximumLength =
                    jdbcTemplate.queryForObject(COLUMN_WIDTH_QUERY, Integer.class, "app_user", "external_id");

            assertThat(characterMaximumLength).isEqualTo(ColumnLimits.EXTERNAL_ID);
        }
    }

    @Nested
    @DisplayName("category.name column width")
    class CategoryName {

        @Test
        @DisplayName(
                "when the migrated schema's character_maximum_length is read for category.name - then it equals ColumnLimits.CATEGORY_NAME")
        void whenMigratedColumnWidthRead_thenEqualsCategoryNameConstant() {
            Integer characterMaximumLength =
                    jdbcTemplate.queryForObject(COLUMN_WIDTH_QUERY, Integer.class, "category", "name");

            assertThat(characterMaximumLength).isEqualTo(ColumnLimits.CATEGORY_NAME);
        }
    }

    @Nested
    @DisplayName("expense.description column width")
    class Description {

        @Test
        @DisplayName(
                "when the migrated schema's character_maximum_length is read for expense.description - then it equals ColumnLimits.DESCRIPTION")
        void whenMigratedColumnWidthRead_thenEqualsDescriptionConstant() {
            Integer characterMaximumLength =
                    jdbcTemplate.queryForObject(COLUMN_WIDTH_QUERY, Integer.class, "expense", "description");

            assertThat(characterMaximumLength).isEqualTo(ColumnLimits.DESCRIPTION);
        }
    }

    @Nested
    @DisplayName("expense.merchant column width")
    class Merchant {

        @Test
        @DisplayName(
                "when the migrated schema's character_maximum_length is read for expense.merchant - then it equals ColumnLimits.MERCHANT")
        void whenMigratedColumnWidthRead_thenEqualsMerchantConstant() {
            Integer characterMaximumLength =
                    jdbcTemplate.queryForObject(COLUMN_WIDTH_QUERY, Integer.class, "expense", "merchant");

            assertThat(characterMaximumLength).isEqualTo(ColumnLimits.MERCHANT);
        }
    }

    @Nested
    @DisplayName("expense.currency_code column width")
    class CurrencyCode {

        @Test
        @DisplayName(
                "when the migrated schema's character_maximum_length is read for expense.currency_code - then it equals ColumnLimits.CURRENCY_CODE")
        void whenMigratedColumnWidthRead_thenEqualsCurrencyCodeConstant() {
            Integer characterMaximumLength =
                    jdbcTemplate.queryForObject(COLUMN_WIDTH_QUERY, Integer.class, "expense", "currency_code");

            assertThat(characterMaximumLength).isEqualTo(ColumnLimits.CURRENCY_CODE);
        }
    }

    @Nested
    @DisplayName("expense_proposal.description column width")
    class ProposalDescription {

        @Test
        @DisplayName(
                "when the migrated schema's character_maximum_length is read for expense_proposal.description - then it equals ColumnLimits.DESCRIPTION")
        void whenMigratedColumnWidthRead_thenEqualsDescriptionConstant() {
            Integer characterMaximumLength =
                    jdbcTemplate.queryForObject(COLUMN_WIDTH_QUERY, Integer.class, "expense_proposal", "description");

            assertThat(characterMaximumLength).isEqualTo(ColumnLimits.DESCRIPTION);
        }
    }

    @Nested
    @DisplayName("expense_proposal.merchant column width")
    class ProposalMerchant {

        @Test
        @DisplayName(
                "when the migrated schema's character_maximum_length is read for expense_proposal.merchant - then it equals ColumnLimits.MERCHANT")
        void whenMigratedColumnWidthRead_thenEqualsMerchantConstant() {
            Integer characterMaximumLength =
                    jdbcTemplate.queryForObject(COLUMN_WIDTH_QUERY, Integer.class, "expense_proposal", "merchant");

            assertThat(characterMaximumLength).isEqualTo(ColumnLimits.MERCHANT);
        }
    }

    @Nested
    @DisplayName("expense_proposal.currency_code column width")
    class ProposalCurrencyCode {

        @Test
        @DisplayName(
                "when the migrated schema's character_maximum_length is read for expense_proposal.currency_code - then it equals ColumnLimits.CURRENCY_CODE")
        void whenMigratedColumnWidthRead_thenEqualsCurrencyCodeConstant() {
            Integer characterMaximumLength =
                    jdbcTemplate.queryForObject(COLUMN_WIDTH_QUERY, Integer.class, "expense_proposal", "currency_code");

            assertThat(characterMaximumLength).isEqualTo(ColumnLimits.CURRENCY_CODE);
        }
    }
}
