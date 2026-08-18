package bot.finance.adapter.cdc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import bot.finance.adapter.persistence.UserEntityRepository;
import bot.finance.common.LogCapture;
import bot.finance.common.ReplicationSlots;
import bot.finance.common.boot.CdcAdapterTest;
import bot.finance.common.boot.CdcAdapterTestOnItsOwnDatabase;
import bot.finance.common.containers.PostgresContainers;
import bot.finance.common.containers.ToxiproxyContainers;
import bot.finance.common.fixtures.ChangeStreamEntries;
import bot.finance.common.fixtures.ChangeStreamEntries.ChangeStreamEntry;
import bot.finance.common.rows.CategoryRowUtils;
import bot.finance.common.rows.ExpenseRowUtils;
import bot.finance.common.rows.OutboxRowUtils;
import bot.finance.common.rows.OutboxRowUtils.OutboxRow;
import bot.finance.common.rows.ProposalReportRowUtils;
import bot.finance.common.rows.SpendingQueryRowUtils;
import bot.finance.common.rows.UserRowUtils;
import bot.finance.domain.value.ExpenseStatus;
import io.debezium.connector.postgresql.connection.PostgresReplicationConnection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

/**
 * Integration test for the outbound adapter owning the embedded Debezium engine's lifecycle. Wires
 * {@link ChangeStreamReader} together with its real collaborators - the real {@link ChangeEventPublisher} chain and
 * the real containerized Postgres and Redis {@link CdcAdapterTest} boots - and calls only the reader's own
 * {@code start()}, {@code stop()} and {@code state()}; nothing is mocked. A captured row is written directly
 * through {@code JdbcAggregateTemplate}, the same idiom {@link CategoryRowUtils} already uses, rather than through
 * a use case, since only the row change reaching the log is under test here.
 */
@CdcAdapterTest
@TestPropertySource(
        properties = {
            "cdc.slot-name=change_stream_reader_test",
            "cdc.heartbeat-interval=1s",
            "cdc.stream-key=change-stream-reader-test.cdc",
            // This class drives the reader itself, one start() and stop() per scenario, so the boot-time
            // lifecycle must not have started it first: a reader already streaming captures the rows a
            // scenario seeds as its "already existed" precondition, and the counts stop meaning anything.
            "cdc.enabled=false"
        })
class ChangeStreamReaderTest {

    private static final String SLOT_NAME = "change_stream_reader_test";
    private static final String STREAM_KEY = "change-stream-reader-test.cdc";
    private static final String PUBLICATION = "finance_ledger_cdc";
    private static final Duration STATE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration EVENT_TIMEOUT = Duration.ofSeconds(15);

    /** The engine's flush trails the record it published, so a position outlives the entry it belongs to. */
    private static final Duration FLUSH_TIMEOUT = Duration.ofSeconds(30);

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
        ReplicationSlots.dropIfUnheld(jdbcTemplate, SLOT_NAME);
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

    private long seedExpense(long userId, long categoryId) {
        return ExpenseRowUtils.storedExpense(
                        jdbcAggregateTemplate,
                        userId,
                        categoryId,
                        "Coffee " + UUID.randomUUID(),
                        "Corner Cafe",
                        350L,
                        "USD",
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        ExpenseStatus.RECORDED)
                .id();
    }

    private void awaitState(ChangeStreamState expected) {
        await().atMost(STATE_TIMEOUT)
                .untilAsserted(() -> assertThat(changeStreamReader.state()).isEqualTo(expected));
    }

    private static String outboxPayload(long userId) {
        return "{\"userId\": %d}".formatted(userId);
    }

    private void awaitEventIdsInOrder(String type, long userId, UUID... expectedIds) {
        List<String> expected = Arrays.stream(expectedIds).map(UUID::toString).toList();
        await().atMost(EVENT_TIMEOUT)
                .untilAsserted(() -> assertThat(ChangeStreamEntries.entriesOnFor(STREAM_KEY, type, userId))
                        .extracting(ChangeStreamEntry::eventId)
                        .containsExactlyElementsOf(expected));
    }

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName("when start() is called against a fresh slot - then only the change made afterward is offered")
        void whenStartedAgainstFreshSlot_thenReachesStreamingAndOnlyChangeMadeAfterwardIsOffered() {
            long userId = seedUser();
            String eventType = "ExpenseRecorded";
            UUID existingId = UUID.randomUUID();
            OutboxRowUtils.storedOutboxRow(jdbcTemplate, existingId, eventType, Instant.now(), outboxPayload(userId));

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            UUID newId = UUID.randomUUID();
            OutboxRowUtils.storedOutboxRow(jdbcTemplate, newId, eventType, Instant.now(), outboxPayload(userId));

            List<ChangeStreamEntry> entries = awaitEntriesFor(eventType, userId, 1);
            assertThat(entries).extracting(ChangeStreamEntry::eventId).containsExactly(newId.toString());
        }

        @Nested
        @DisplayName("when the database's wal_level is not logical")
        @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
        @CdcAdapterTestOnItsOwnDatabase
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

        @Nested
        @DisplayName("when the publication the connector streams from is absent")
        @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
        @CdcAdapterTestOnItsOwnDatabase
        @TestPropertySource(properties = "cdc.slot-name=change_stream_reader_test_publication")
        class PublicationAbsent {

            private static final String PUBLICATION_SLOT_NAME = "change_stream_reader_test_publication";

            /**
             * A private Postgres at {@code wal_level=logical} whose publication this scenario drops, rather than
             * the shared {@link bot.finance.common.containers.PostgresContainers} singleton: every other capture
             * test streams from that one publication, and dropping it underneath them would take their engines
             * down with it.
             */
            @Container
            private static final PostgreSQLContainer<?> PUBLICATION_POSTGRES =
                    new PostgreSQLContainer<>("postgres:18").withCommand("postgres", "-c", "wal_level=logical");

            @DynamicPropertySource
            static void datasourceProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", PUBLICATION_POSTGRES::getJdbcUrl);
                registry.add("spring.datasource.username", PUBLICATION_POSTGRES::getUsername);
                registry.add("spring.datasource.password", PUBLICATION_POSTGRES::getPassword);
            }

            @Autowired
            private ChangeStreamReader readerAgainstMissingPublication;

            @Autowired
            private JdbcTemplate jdbcTemplateAgainstMissingPublication;

            @Test
            @DisplayName("when start() is called - then the reader reports DOWN without taking the slot")
            void whenStartIsCalled_thenReportsDownWithoutTakingTheSlot() {
                jdbcTemplateAgainstMissingPublication.execute("DROP PUBLICATION finance_ledger_cdc");

                readerAgainstMissingPublication.start();

                await().pollDelay(Duration.ofSeconds(3)).atMost(STATE_TIMEOUT).untilAsserted(() -> assertThat(
                                readerAgainstMissingPublication.state())
                        .isEqualTo(ChangeStreamState.DOWN));
                assertThat(ReplicationSlots.walStatus(jdbcTemplateAgainstMissingPublication, PUBLICATION_SLOT_NAME))
                        .isEmpty();
            }
        }

        @Test
        @DisplayName("when a stored position exists - then streaming resumes and every change since is offered "
                + "in order")
        void whenStoredPositionExistsFromEarlierRun_thenStreamingResumesAndEveryChangeIsOfferedInOrder() {
            long userId = seedUser();
            String eventType = "ExpenseRecorded";

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);
            changeStreamReader.stop(Duration.ofSeconds(5));

            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            OutboxRowUtils.storedOutboxRow(jdbcTemplate, firstId, eventType, Instant.now(), outboxPayload(userId));
            OutboxRowUtils.storedOutboxRow(jdbcTemplate, secondId, eventType, Instant.now(), outboxPayload(userId));

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            awaitEventIdsInOrder(eventType, userId, firstId, secondId);
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
                    LocalDate.now().minusDays(7),
                    LocalDate.now(),
                    Instant.now());
            ProposalReportRowUtils.storedReport(
                    jdbcAggregateTemplate,
                    userId,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    Instant.now());
            jdbcTemplate.update("UPDATE cdc_heartbeat SET beat_at = now()");

            assertThat(ChangeStreamEntries.allEntriesOn(STREAM_KEY)).noneMatch(entry -> entry.userId() == userId);
        }

        @Test
        @DisplayName("when a category is renamed and an expense is updated by SQL - then nothing reaches the stream")
        void whenCategoryRenamedAndExpenseUpdatedBySql_thenNothingReachesTheStream() {
            long userId = seedUser();
            long groupingId = seedGrouping(userId);
            long categoryId = seedCategory(userId, groupingId);
            long expenseId = seedExpense(userId, categoryId);

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            CategoryRowUtils.renameCategory(jdbcAggregateTemplate, userId, categoryId, "Renamed " + UUID.randomUUID());
            jdbcTemplate.update(
                    "UPDATE expense SET description = ? WHERE id = ?", "Updated " + UUID.randomUUID(), expenseId);

            // Neither table is in table.include.list any more, so nothing the engine offers can carry this user -
            // the heartbeat-driven slot advance is what proves the writes were actually seen rather than the
            // engine never catching up to them.
            String initialLsn = confirmedFlushLsn();
            await().atMost(Duration.ofSeconds(20))
                    .untilAsserted(() -> assertThat(confirmedFlushLsn()).isNotEqualTo(initialLsn));

            assertThat(ChangeStreamEntries.allEntriesOn(STREAM_KEY)).noneMatch(entry -> entry.userId() == userId);
        }

        @Test
        @DisplayName("when two outbox rows are inserted then deleted - then the stream gains exactly two entries")
        void whenTwoOutboxRowsInsertedThenDeleted_thenStreamGainsExactlyTwoEntries() {
            long userId = seedUser();
            String eventType = "ExpenseRecorded";

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            OutboxRowUtils.storedOutboxRow(jdbcTemplate, firstId, eventType, Instant.now(), outboxPayload(userId));
            OutboxRowUtils.storedOutboxRow(jdbcTemplate, secondId, eventType, Instant.now(), outboxPayload(userId));
            List<OutboxRow> insertedRows = OutboxRowUtils.outboxRowsFor(jdbcTemplate, userId);

            jdbcTemplate.update("DELETE FROM outbox WHERE id = ?", firstId);
            jdbcTemplate.update("DELETE FROM outbox WHERE id = ?", secondId);

            // The publication only carries insert and update (V010), so a third entry here would mean the
            // delete leaked through rather than just the two inserts reaching the stream.
            List<ChangeStreamEntry> entries = awaitEntriesFor(eventType, userId, 2);

            assertThat(entries)
                    .extracting(ChangeStreamEntry::eventId)
                    .containsExactlyInAnyOrderElementsOf(
                            insertedRows.stream().map(row -> row.id().toString()).toList());
            assertThat(entries).allSatisfy(entry -> assertThat(entry.type()).isEqualTo(eventType));
            assertThat(entries).extracting(ChangeStreamEntry::occurredAt).doesNotContainNull();
            assertThat(entries)
                    .extracting(entry -> entry.payload().path("userId").asLong())
                    .containsOnly(userId);
        }

        @Test
        @DisplayName("when only uncaptured tables are written past the heartbeat interval - then the slot "
                + "advances and nothing is offered")
        void whenOnlyUncapturedTablesWrittenPastHeartbeat_thenSlotPositionAdvancesAndNothingOffered() {
            long userId = seedUser();

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);
            String initialLsn = confirmedFlushLsn();

            await().atMost(Duration.ofSeconds(20))
                    .untilAsserted(() -> assertThat(confirmedFlushLsn()).isNotEqualTo(initialLsn));

            assertThat(ChangeStreamEntries.allEntriesOn(STREAM_KEY)).noneMatch(entry -> entry.userId() == userId);
        }

        @Nested
        @DisplayName("when Redis refuses the write")
        @TestPropertySource(
                properties = {
                    "cdc.slot-name=change_stream_reader_test_redis_down",
                    "cdc.stream-key=change-stream-reader-test-redis-down.cdc",
                    // The heartbeat moves the slot forward on its own, and this scenario reads the slot to
                    // learn whether the held-back change moved it. An interval outlasting the test leaves the
                    // change as the only thing that could.
                    "cdc.heartbeat-interval=1h"
                })
        class RedisUnavailable {

            private static final String REDIS_DOWN_STREAM_KEY = "change-stream-reader-test-redis-down.cdc";

            @Autowired
            private ChangeStreamReader readerAgainstDeadRedis;

            @Autowired
            private JdbcTemplate jdbcTemplateInDeadRedisContext;

            @Autowired
            private UserEntityRepository userEntityRepositoryInDeadRedisContext;

            @Test
            @DisplayName("when Redis recovers after refusing a write - then the entry reaches the stream exactly "
                    + "once")
            void whenRedisRecoversAfterRefusingWrite_thenEntryReachesStreamExactlyOnce() {
                // Redis is refused at the proxy rather than by pointing this context at a closed port: the
                // capture annotation registers the Redis URL through a bean applied during the refresh, which
                // outranks a property a class sets for itself, so such an override would silently do nothing.
                ToxiproxyContainers.REDIS_PROXY.setConnectionCut(true);
                try {
                    long userId = UserRowUtils.storedUserId(
                            userEntityRepositoryInDeadRedisContext, "redis-down-" + UUID.randomUUID());
                    String eventType = "ExpenseRecorded";

                    readerAgainstDeadRedis.start();
                    awaitStateAgainstDeadRedis(ChangeStreamState.STREAMING);

                    UUID eventId = UUID.randomUUID();
                    OutboxRowUtils.storedOutboxRow(
                            jdbcTemplateInDeadRedisContext, eventId, eventType, Instant.now(), outboxPayload(userId));

                    // A refused publish is the only thing that takes a streaming reader back to DOWN, so
                    // reaching it is what establishes the engine got to the change before Redis is restored.
                    awaitStateAgainstDeadRedis(ChangeStreamState.DOWN);

                    ToxiproxyContainers.REDIS_PROXY.setConnectionCut(false);
                    awaitStateAgainstDeadRedis(ChangeStreamState.STREAMING);

                    List<ChangeStreamEntry> entries = awaitEntriesForInDeadRedisContext(eventType, userId, 1);
                    assertThat(entries)
                            .extracting(ChangeStreamEntry::eventId)
                            .containsExactly(eventId.toString());
                } finally {
                    ToxiproxyContainers.REDIS_PROXY.setConnectionCut(false);
                    readerAgainstDeadRedis.stop(Duration.ofSeconds(5));
                }
            }

            private void awaitStateAgainstDeadRedis(ChangeStreamState expected) {
                await().atMost(EVENT_TIMEOUT).untilAsserted(() -> assertThat(readerAgainstDeadRedis.state())
                        .isEqualTo(expected));
            }

            private List<ChangeStreamEntry> awaitEntriesForInDeadRedisContext(String type, long userId, int expectedCount) {
                await().atMost(EVENT_TIMEOUT)
                        .untilAsserted(() -> assertThat(
                                        ChangeStreamEntries.entriesOnFor(REDIS_DOWN_STREAM_KEY, type, userId))
                                .hasSize(expectedCount));
                return ChangeStreamEntries.entriesOnFor(REDIS_DOWN_STREAM_KEY, type, userId);
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
            String eventType = "ExpenseRecorded";

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            UUID firstId = UUID.randomUUID();
            OutboxRowUtils.storedOutboxRow(jdbcTemplate, firstId, eventType, Instant.now(), outboxPayload(userId));
            long firstChangeLsn = currentWalLsnOffset();

            boolean stoppedInTime = changeStreamReader.stop(Duration.ofSeconds(5));
            assertThat(stoppedInTime).isTrue();

            boolean slotStillExists = Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_replication_slots WHERE slot_name = ?)",
                    Boolean.class,
                    SLOT_NAME));
            assertThat(slotStillExists).isTrue();

            // stop() returns once the engine's task has finished, which can be before the position it reached
            // is durable. A restart over a position still behind the first change resumes from before it.
            awaitPositionCommittedPast(firstChangeLsn);

            UUID secondId = UUID.randomUUID();
            OutboxRowUtils.storedOutboxRow(jdbcTemplate, secondId, eventType, Instant.now(), outboxPayload(userId));

            changeStreamReader.start();
            awaitState(ChangeStreamState.STREAMING);

            awaitEventIdsInOrder(eventType, userId, firstId, secondId);
        }

        @Test
        @DisplayName("when stopped while its connector is still retrying a slot held elsewhere - then it stops "
                + "within ten seconds")
        void whenStoppedWhileConnectorStillTryingToOpenHeldSlot_thenStopsWithinTenSeconds() throws Exception {
            ReplicationSlots.create(jdbcTemplate, SLOT_NAME);
            try (AutoCloseable heldElsewhere = ReplicationSlots.hold(
                            PostgresContainers.POSTGRES_CONTAINER.getJdbcUrl(),
                            PostgresContainers.POSTGRES_CONTAINER.getUsername(),
                            PostgresContainers.POSTGRES_CONTAINER.getPassword(),
                            SLOT_NAME,
                            PUBLICATION);
                    LogCapture connectorLog = LogCapture.attachedTo(PostgresReplicationConnection.class)) {
                changeStreamReader.start();
                // The connector tries the slot again from a thread of its own, and leaves no trace of that but
                // this line - so the line is what says the trying has begun.
                await().atMost(STATE_TIMEOUT).untilAsserted(() -> assertThat(connectorLog.messages())
                        .anyMatch(message -> message.startsWith("Failed to start replication stream")));

                assertThat(changeStreamReader.stop(Duration.ofSeconds(10))).isTrue();
            }
        }
    }

    private static List<ChangeStreamEntry> awaitEntriesFor(String table, long userId, int expectedCount) {
        await().atMost(EVENT_TIMEOUT)
                .untilAsserted(() -> assertThat(ChangeStreamEntries.entriesOnFor(STREAM_KEY, table, userId))
                        .hasSize(expectedCount));
        return ChangeStreamEntries.entriesOnFor(STREAM_KEY, table, userId);
    }

    private String confirmedFlushLsn() {
        return confirmedFlushLsn(jdbcTemplate, SLOT_NAME);
    }

    private static String confirmedFlushLsn(JdbcTemplate template, String slotName) {
        return template.queryForObject(
                "SELECT confirmed_flush_lsn::text FROM pg_replication_slots WHERE slot_name = ?",
                String.class,
                slotName);
    }

    /**
     * The current log position as an offset from {@code 0/0}, comparable with {@link #positionPassed(long)}'s
     * {@code confirmed_flush_lsn} check - the same function {@code ReplicationCatalogue} reads.
     */
    private long currentWalLsnOffset() {
        Number offset = jdbcTemplate.queryForObject("SELECT pg_current_wal_lsn() - '0/0'::pg_lsn", Number.class);
        return offset == null ? 0 : offset.longValue();
    }

    private void awaitPositionCommittedPast(long lsn) {
        await().atMost(FLUSH_TIMEOUT)
                .untilAsserted(() -> assertThat(positionPassed(lsn)).isTrue());
    }

    private Boolean positionPassed(long lsn) {
        return jdbcTemplate.queryForObject(
                """
                SELECT confirmed_flush_lsn >= '0/0'::pg_lsn + ?::numeric
                FROM pg_replication_slots WHERE slot_name = ?
                """,
                Boolean.class,
                lsn,
                SLOT_NAME);
    }
}
