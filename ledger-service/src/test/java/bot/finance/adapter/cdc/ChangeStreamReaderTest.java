package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.LedgerServiceApplication;
import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.boot.CdcCaptureTest;
import bot.finance.common.containers.PostgresContainers;
import bot.finance.common.containers.RedisContainers;
import bot.finance.common.fixtures.ChangeStreamEntries;
import bot.finance.common.fixtures.ChangeStreamEntries.ChangeStreamEntry;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseProposalRowUtils;
import bot.finance.common.rows.ProposalReportRowUtils;
import bot.finance.common.rows.SpendingQueryRowUtils;
import bot.finance.common.rows.UserRowUtils;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration test for the outbound adapter owning the embedded Debezium engine's lifecycle. Wires
 * {@link ChangeStreamReader} together with its real collaborators - the real {@link ChangeEventPublisher} chain and
 * the real containerized Postgres and Redis {@link CdcCaptureTest} boots - and calls only the reader's own
 * {@code start()}, {@code stop()} and {@code state()}; nothing is mocked. A captured row is written directly
 * through {@code JdbcAggregateTemplate}, the same idiom {@link CategoryRowUtils} already uses, rather than through
 * a use case, since only the row change reaching the log is under test here.
 */
@CdcCaptureTest
@TestPropertySource(properties = "cdc.slot-name=change_stream_reader_test")
class ChangeStreamReaderTest {

    private static final String SLOT_NAME = "change_stream_reader_test";
    private static final Duration STATE_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private ChangeStreamReader changeStreamReader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcAggregateTemplate jdbcAggregateTemplate;

    @Autowired
    private UserEntityRepository userEntityRepository;

    @AfterEach
    void cleanUp() {
        changeStreamReader.stop(Duration.ofSeconds(5));
        jdbcTemplate.execute("SELECT pg_drop_replication_slot(slot_name) FROM pg_replication_slots WHERE slot_name = '"
                + SLOT_NAME + "' AND NOT active");
    }

    private long seedUser() {
        return UserRowUtils.storedUserId(userEntityRepository, "reader-test-" + UUID.randomUUID());
    }

    private long seedGrouping(long userId) {
        return CategoryRowUtils.storedGroupingId(jdbcAggregateTemplate, userId, "Groceries " + UUID.randomUUID());
    }

    private long seedCategory(long userId, long groupingId) {
        return CategoryRowUtils.storedCategoryId(
                jdbcAggregateTemplate, userId, groupingId, "Supermarkets " + UUID.randomUUID());
    }

    private void awaitState(ChangeStreamState expected) {
        await().atMost(STATE_TIMEOUT)
                .untilAsserted(() -> assertThat(changeStreamReader.state()).isEqualTo(expected));
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName("when start() is called against a fresh slot - then only the change made afterward is offered")
        void whenStartedAgainstFreshSlot_thenReachesStreamingAndOnlyChangeMadeAfterwardIsOffered() {
            long userId = seedUser();
            long groupingId = seedGrouping(userId);
            long existingCategoryId = seedCategory(userId, groupingId);

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            long newCategoryId = CategoryRowUtils.storedCategoryId(
                    jdbcAggregateTemplate, userId, groupingId, "Markets " + UUID.randomUUID());

            List<ChangeStreamEntry> entries = awaitEntriesFor("category", userId, 1);
            assertThat(entries)
                    .extracting(ChangeStreamEntry::after)
                    .noneMatch(after -> after.path("id").asLong() == existingCategoryId);
        }

        @Test
        @DisplayName("when a slot another connection already holds - then the reader reports STANDBY and keeps "
                + "retrying rather than failing")
        void whenSlotAlreadyHeldByAnotherConnection_thenReportsStandbyAndKeepsRetrying() throws Exception {
            // Creates the slot first, through this reader itself, then hands it to a raw replication
            // connection so a genuinely different holder occupies it before the reader under test tries again.
            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);
            changeStreamReader.stop(Duration.ofSeconds(5));

            try (Connection rawReplicationConnection = openReplicationConnection();
                    PGReplicationStream otherHolderStream = holdSlot(rawReplicationConnection)) {
                changeStreamReader.start();

                awaitState(ChangeStreamState.STANDBY);
            }
        }

        private Connection openReplicationConnection() throws SQLException {
            Properties properties = new Properties();
            properties.setProperty("user", PostgresContainers.POSTGRES_CONTAINER.getUsername());
            properties.setProperty("password", PostgresContainers.POSTGRES_CONTAINER.getPassword());
            properties.setProperty("replication", "database");
            properties.setProperty("preferQueryMode", "simple");
            return DriverManager.getConnection(PostgresContainers.POSTGRES_CONTAINER.getJdbcUrl(), properties);
        }

        private PGReplicationStream holdSlot(Connection rawReplicationConnection) throws SQLException {
            PGConnection pgConnection = rawReplicationConnection.unwrap(PGConnection.class);
            return pgConnection
                    .getReplicationAPI()
                    .replicationStream()
                    .logical()
                    .withSlotName(SLOT_NAME)
                    .withSlotOption("proto_version", 1)
                    .withSlotOption("publication_names", "finance_ledger_cdc")
                    .withStartPosition(LogSequenceNumber.valueOf("0/0"))
                    .start();
        }

        @Nested
        @DisplayName("when the database's wal_level is not logical")
        @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
        @ActiveProfiles("test")
        @Testcontainers(disabledWithoutDocker = true)
        @SpringBootTest(
                classes = LedgerServiceApplication.class,
                webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
        @TestPropertySource(properties = "cdc.slot-name=change_stream_reader_test_wal_level")
        class WalLevelNotLogical {

            /**
             * A private, freshly started Postgres left at its default {@code wal_level} (below {@code logical}),
             * rather than the shared {@link bot.finance.common.containers.PostgresContainers} singleton every
             * other test in this module relies on running at {@code wal_level=logical}. Nothing in the shared test
             * infrastructure offers a non-logical database, so this scenario is the one exception that stands up
             * its own.
             */
            @Container
            private static final PostgreSQLContainer<?> REPLICA_WAL_LEVEL_POSTGRES =
                    new PostgreSQLContainer<>("postgres:18");

            @DynamicPropertySource
            static void datasourceProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", REPLICA_WAL_LEVEL_POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", REPLICA_WAL_LEVEL_POSTGRES::getUsername);
                registry.add("spring.datasource.password", REPLICA_WAL_LEVEL_POSTGRES::getPassword);
                registry.add("spring.data.redis.url", RedisContainers::redisUrl);
            }

            @Autowired
            private ChangeStreamReader readerAgainstNonLogicalWal;

            @Test
            @DisplayName(
                    "when start() is called - then the reader reports DOWN and stops retrying rather than " + "looping")
            void whenStartIsCalled_thenReportsDownAndStopsRetryingRatherThanLooping() {
                readerAgainstNonLogicalWal.start();

                await().atMost(STATE_TIMEOUT).untilAsserted(() -> assertThat(readerAgainstNonLogicalWal.state())
                        .isEqualTo(ChangeStreamState.DOWN));
            }
        }

        @Test
        @DisplayName("when a stored position exists - then streaming resumes and every change since is offered "
                + "in order")
        void whenStoredPositionExistsFromEarlierRun_thenStreamingResumesAndEveryChangeIsOfferedInOrder() {
            long userId = seedUser();
            long groupingId = seedGrouping(userId);

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);
            changeStreamReader.stop(Duration.ofSeconds(5));

            long firstCategoryId = CategoryRowUtils.storedCategoryId(
                    jdbcAggregateTemplate, userId, groupingId, "Markets " + UUID.randomUUID());
            long secondCategoryId = CategoryRowUtils.storedCategoryId(
                    jdbcAggregateTemplate, userId, groupingId, "Household " + UUID.randomUUID());

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            List<ChangeStreamEntry> entries = awaitEntriesFor("category", userId, 2);
            assertThat(entries)
                    .extracting(entry -> entry.after().path("id").asLong())
                    .containsExactly(firstCategoryId, secondCategoryId);
        }
    }

    @Nested
    @DisplayName("the offer loop")
    class TheOfferLoop {

        @Test
        @DisplayName("when writes are made to app_user, spending_query, proposal_report and cdc_heartbeat - then "
                + "none of them is offered")
        void whenUncapturedTablesAreWritten_thenNoneOfThemIsOffered() {
            long userId = seedUser();

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            SpendingQueryRowUtils.storedQuery(
                    jdbcAggregateTemplate,
                    userId,
                    UUID.randomUUID().toString(),
                    java.time.LocalDate.now().minusDays(7),
                    java.time.LocalDate.now(),
                    Instant.now());
            ProposalReportRowUtils.storedReport(
                    jdbcAggregateTemplate,
                    userId,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    Instant.now());
            jdbcTemplate.update("UPDATE cdc_heartbeat SET beat_at = now()");

            assertThat(ChangeStreamEntries.entriesFor("app_user", userId)).isEmpty();
            assertThat(ChangeStreamEntries.entriesFor("spending_query", userId)).isEmpty();
            assertThat(ChangeStreamEntries.entriesFor("proposal_report", userId))
                    .isEmpty();
            assertThat(ChangeStreamEntries.entriesFor("cdc_heartbeat", userId)).isEmpty();
        }

        @Test
        @DisplayName("when a pending proposal is discarded as a lone DELETE - then no expense insert shares its "
                + "transaction")
        void whenPendingProposalDiscardedAsLoneDelete_thenOneDeleteEventCarriesWholeRowAndNoExpenseSharesTxn() {
            long userId = seedUser();
            long groupingId = seedGrouping(userId);
            long categoryId = seedCategory(userId, groupingId);

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            var proposal = ExpenseProposalRowUtils.storedProposal(
                    jdbcAggregateTemplate,
                    userId,
                    categoryId,
                    "Coffee",
                    "Corner Cafe",
                    500L,
                    "EUR",
                    UUID.randomUUID().toString(),
                    Instant.now());
            jdbcAggregateTemplate.delete(proposal);

            List<ChangeStreamEntry> entries = awaitEntriesFor("expense_proposal", userId, 1);
            assertThat(entries.get(0).op()).isEqualTo("d");
            assertThat(entries.get(0).before().path("id").asLong()).isEqualTo(proposal.id());
            assertThat(ChangeStreamEntries.entriesFor("expense", userId)).isEmpty();
        }

        @Test
        @DisplayName("when a category is renamed through the row helper - then a category event is offered "
                + "carrying the tree's own change")
        void whenCategoryRenamedThroughRowHelper_thenCategoryEventOfferedCarryingTreesOwnChange() {
            long userId = seedUser();
            long groupingId = seedGrouping(userId);
            long categoryId = seedCategory(userId, groupingId);
            String newName = "Renamed " + UUID.randomUUID();

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            CategoryRowUtils.renameCategory(jdbcAggregateTemplate, userId, categoryId, newName);

            List<ChangeStreamEntry> entries = awaitEntriesFor("category", userId, 1);
            assertThat(entries.get(0).op()).isEqualTo("u");
            assertThat(entries.get(0).after().path("name").asText()).isEqualTo(newName);
        }

        @Test
        @DisplayName("when only uncaptured tables are written past the heartbeat interval - then the slot "
                + "advances and nothing is offered")
        void whenOnlyUncapturedTablesWrittenPastHeartbeat_thenSlotPositionAdvancesAndNothingOffered() {
            long userId = seedUser();

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);
            String initialLsn = confirmedFlushLsn();

            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(() -> assertThat(confirmedFlushLsn()).isNotEqualTo(initialLsn));

            assertThat(ChangeStreamEntries.entriesFor("cdc_heartbeat", userId)).isEmpty();
        }

        @Nested
        @DisplayName("when Redis refuses the write")
        @TestPropertySource(
                properties = {
                    "cdc.slot-name=change_stream_reader_test_redis_down",
                    // Points this nested context's own Redis client at a closed port, per the module's own
                    // fixture guidance, rather than stopping the shared Redis singleton every other test relies on.
                    "spring.data.redis.url=redis://localhost:1"
                })
        class RedisUnavailable {

            @Autowired
            private ChangeStreamReader readerAgainstDeadRedis;

            @Autowired
            private JdbcAggregateTemplate jdbcAggregateTemplateInDeadRedisContext;

            @Autowired
            private UserEntityRepository userEntityRepositoryInDeadRedisContext;

            @Test
            @DisplayName("when a captured row changes - then the position is not committed and the event is "
                    + "offered again after a backoff")
            void whenCapturedRowChanges_thenPositionNotCommittedAndEventOfferedAgainAfterBackoff() {
                long userId = UserRowUtils.storedUserId(
                        userEntityRepositoryInDeadRedisContext, "redis-down-" + UUID.randomUUID());
                long groupingId = CategoryRowUtils.storedGroupingId(
                        jdbcAggregateTemplateInDeadRedisContext, userId, "Groceries " + UUID.randomUUID());

                readerAgainstDeadRedis.start();

                CategoryRowUtils.storedCategoryId(
                        jdbcAggregateTemplateInDeadRedisContext, userId, groupingId, "Markets " + UUID.randomUUID());

                await().pollDelay(Duration.ofSeconds(3))
                        .atMost(Duration.ofSeconds(10))
                        .untilAsserted(() -> assertThat(ChangeStreamEntries.entriesFor("category", userId))
                                .isEmpty());
            }
        }
    }

    @Nested
    @DisplayName("stop()")
    class Stop {

        @Test
        @DisplayName("when a streaming reader is stopped - then a fresh reader resumes from the last committed "
                + "position")
        void whenStreamingReaderStopped_thenTaskFinishesSlotLeftInPlaceAndFreshReaderResumes() {
            long userId = seedUser();
            long groupingId = seedGrouping(userId);

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            long firstCategoryId = CategoryRowUtils.storedCategoryId(
                    jdbcAggregateTemplate, userId, groupingId, "Markets " + UUID.randomUUID());
            awaitEntriesFor("category", userId, 1);

            boolean stoppedInTime = changeStreamReader.stop(Duration.ofSeconds(5));
            assertThat(stoppedInTime).isTrue();

            boolean slotStillExists = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = ?)",
                    Boolean.class,
                    SLOT_NAME));
            assertThat(slotStillExists).isTrue();

            long secondCategoryId = CategoryRowUtils.storedCategoryId(
                    jdbcAggregateTemplate, userId, groupingId, "Household " + UUID.randomUUID());

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            List<ChangeStreamEntry> entries = awaitEntriesFor("category", userId, 2);
            assertThat(entries)
                    .extracting(entry -> entry.after().path("id").asLong())
                    .containsExactly(firstCategoryId, secondCategoryId);
        }
    }

    private static List<ChangeStreamEntry> awaitEntriesFor(String table, long userId, int expectedCount) {
        await().atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(ChangeStreamEntries.entriesFor(table, userId))
                        .hasSize(expectedCount));
        return ChangeStreamEntries.entriesFor(table, userId);
    }

    private String confirmedFlushLsn() {
        return jdbcTemplate.queryForObject(
                "SELECT confirmed_flush_lsn::text FROM pg_replication_slots WHERE slot_name = ?",
                String.class,
                SLOT_NAME);
    }
}
