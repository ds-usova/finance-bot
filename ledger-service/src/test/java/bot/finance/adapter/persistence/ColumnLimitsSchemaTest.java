package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.value.ExpenseStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
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

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Nested
    @DisplayName("app_user.external_id column width")
    class ExternalId {

        @Test
        @DisplayName(
                "when the migrated width of app_user.external_id is read - then it equals ColumnLimits.EXTERNAL_ID")
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
        @DisplayName("when the migrated width of category.name is read - then it equals ColumnLimits.CATEGORY_NAME")
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
        @DisplayName("when the migrated width of expense.description is read - then it equals ColumnLimits.DESCRIPTION")
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
        @DisplayName("when the migrated width of expense.merchant is read - then it equals ColumnLimits.MERCHANT")
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
                "when the migrated width of expense.currency_code is read - then it equals ColumnLimits.CURRENCY_CODE")
        void whenMigratedColumnWidthRead_thenEqualsCurrencyCodeConstant() {
            Integer characterMaximumLength =
                    jdbcTemplate.queryForObject(COLUMN_WIDTH_QUERY, Integer.class, "expense", "currency_code");

            assertThat(characterMaximumLength).isEqualTo(ColumnLimits.CURRENCY_CODE);
        }
    }

    @Nested
    @DisplayName("the pending-has-message check")
    class PendingHasMessage {

        @Test
        @DisplayName(
                "when a PENDING row has no incoming_message_id - then the database refuses it under ck_expense_pending_has_message")
        void whenPendingRowHasNoIncomingMessageId_thenDatabaseRefusesUnderPendingHasMessageConstraint() {
            long userId = UserRowUtils.storedUserId(userEntityRepository, "column-limits-pending-no-message-user");
            long categoryId = CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, "Groceries");

            assertThatThrownBy(() -> ExpenseRowUtils.storedExpense(
                            jdbcAggregateTemplate,
                            userId,
                            categoryId,
                            "Awaiting confirmation",
                            null,
                            100,
                            "USD",
                            null,
                            Instant.now(),
                            ExpenseStatus.PENDING))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_expense_pending_has_message");
        }
    }
}
