package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.boot.TheDatabaseSlice;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.UserRowUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Proves {@code V009__merge_expense_proposal_into_expense.sql}'s data move: a database of the class's own,
 * migrated to V008 and seeded the way a production database would have accumulated rows, carried through the
 * remaining migrations. {@link bot.finance.common.containers.PostgresContainers}, the singleton every other
 * persistence test shares, is already past V009 by the time a test class touches it, so proving what the
 * migration does to already-stored rows needs a database still at V008 when the class starts - on the pattern
 * {@link bot.finance.common.boot.CdcAdapterTestOnItsOwnDatabase} sets for a class that must not join or disturb
 * that singleton.
 *
 * <p>Spring Boot's own Flyway autoconfiguration is switched off so the two migration phases stay under the
 * test's control: one {@link Flyway} instance targeted at V008 boots the pre-merge schema, the fixture rows are
 * seeded directly against it, and a second instance with no target carries the database through V009 and V010.
 */
@TheDatabaseSlice
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = "spring.flyway.enabled=false")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExpenseProposalMergeMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserEntityRepository userEntityRepository;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** The pre-V009 shape of {@code expense}: no {@code status} column yet. */
    @Table("expense")
    private record PreMigrationExpenseRow(
            @Id Long id,
            Long userId,
            Long categoryId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            String incomingMessageId,
            Instant createdAt,
            Instant updatedAt) {}

    /** The pre-V009 {@code expense_proposal} table, dropped once the merge migration runs. */
    @Table("expense_proposal")
    private record PreMigrationProposalRow(
            @Id Long id,
            Long userId,
            Long categoryId,
            String description,
            String merchant,
            long amountMinorUnits,
            String currencyCode,
            String incomingMessageId,
            Instant createdAt,
            Instant updatedAt) {}

    @Nested
    @DisplayName("carrying a pre-merge database through the remaining migrations")
    class MergingProposalsIntoExpense {

        @Test
        @DisplayName(
                "when the remaining migrations run - then expenses keep their id, proposals become PENDING, and the table is gone")
        void whenRemainingMigrationsRun_thenExpensesKeepIdProposalsBecomePendingAndTableIsGone() {
            migrateTo("8");

            long userId = UserRowUtils.storedUserId(userEntityRepository, "merge-migration-user");
            long groupingId = CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, "Groceries");
            long categoryId =
                    CategoryRowUtils.storedCategoryId(jdbcAggregateTemplate, userId, groupingId, "Supermarkets");
            long otherUserId = UserRowUtils.storedUserId(userEntityRepository, "merge-migration-other-user");
            long otherGroupingId = CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, otherUserId, "Dining");
            long otherCategoryId = CategoryRowUtils.storedCategoryId(
                    jdbcAggregateTemplate, otherUserId, otherGroupingId, "Restaurants");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

            PreMigrationExpenseRow firstExpense = jdbcAggregateTemplate.insert(new PreMigrationExpenseRow(
                    null, userId, categoryId, "First recorded", "Trader Joe's", 1500, "USD", null, now, now));
            PreMigrationExpenseRow secondExpense = jdbcAggregateTemplate.insert(new PreMigrationExpenseRow(
                    null, otherUserId, otherCategoryId, "Second recorded", null, 2500, "EUR", null, now, now));

            String firstProposalMessageId = UUID.randomUUID().toString();
            String secondProposalMessageId = UUID.randomUUID().toString();
            String thirdProposalMessageId = UUID.randomUUID().toString();
            jdbcAggregateTemplate.insert(new PreMigrationProposalRow(
                    null,
                    userId,
                    categoryId,
                    "First proposal",
                    "Corner Cafe",
                    800,
                    "USD",
                    firstProposalMessageId,
                    now,
                    now));
            jdbcAggregateTemplate.insert(new PreMigrationProposalRow(
                    null, userId, categoryId, "Second proposal", null, 300, "USD", secondProposalMessageId, now, now));
            jdbcAggregateTemplate.insert(new PreMigrationProposalRow(
                    null,
                    otherUserId,
                    otherCategoryId,
                    "Third proposal",
                    "Market",
                    1200,
                    "EUR",
                    thirdProposalMessageId,
                    now,
                    now));

            migrateTo(null);

            Integer proposalTableCount = jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema = current_schema() AND table_name = 'expense_proposal'
                    """,
                    Integer.class);
            assertThat(proposalTableCount).isZero();

            assertThat(expenseRow(firstExpense.id())).satisfies(row -> {
                assertThat(row.get("status")).isEqualTo("RECORDED");
                assertThat(row.get("description")).isEqualTo("First recorded");
                assertThat(row.get("merchant")).isEqualTo("Trader Joe's");
                assertThat(row.get("amount_minor_units")).isEqualTo(1500L);
                assertThat(row.get("currency_code")).isEqualTo("USD");
                assertThat(row.get("category_id")).isEqualTo(categoryId);
                assertThat(row.get("user_id")).isEqualTo(userId);
            });
            assertThat(expenseRow(secondExpense.id())).satisfies(row -> {
                assertThat(row.get("status")).isEqualTo("RECORDED");
                assertThat(row.get("description")).isEqualTo("Second recorded");
                assertThat(row.get("merchant")).isNull();
            });

            List<Map<String, Object>> pendingRows =
                    jdbcTemplate.queryForList("SELECT * FROM expense WHERE status = 'PENDING' ORDER BY description");
            assertThat(pendingRows).hasSize(3);
            assertThat(pendingRows)
                    .anySatisfy(row -> {
                        assertThat(row.get("description")).isEqualTo("First proposal");
                        assertThat(row.get("merchant")).isEqualTo("Corner Cafe");
                        assertThat(row.get("amount_minor_units")).isEqualTo(800L);
                        assertThat(row.get("currency_code")).isEqualTo("USD");
                        assertThat(row.get("incoming_message_id")).isEqualTo(firstProposalMessageId);
                        assertThat(row.get("category_id")).isEqualTo(categoryId);
                        assertThat(row.get("user_id")).isEqualTo(userId);
                    })
                    .anySatisfy(row -> {
                        assertThat(row.get("description")).isEqualTo("Second proposal");
                        assertThat(row.get("merchant")).isNull();
                        assertThat(row.get("incoming_message_id")).isEqualTo(secondProposalMessageId);
                    })
                    .anySatisfy(row -> {
                        assertThat(row.get("description")).isEqualTo("Third proposal");
                        assertThat(row.get("incoming_message_id")).isEqualTo(thirdProposalMessageId);
                        assertThat(row.get("user_id")).isEqualTo(otherUserId);
                    });
        }
    }

    private Map<String, Object> expenseRow(long id) {
        return jdbcTemplate.queryForMap("SELECT * FROM expense WHERE id = ?", id);
    }

    private static void migrateTo(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }
}
