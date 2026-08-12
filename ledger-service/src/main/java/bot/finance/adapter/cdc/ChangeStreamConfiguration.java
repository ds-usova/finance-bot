package bot.finance.adapter.cdc;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * Wires the embedded Debezium engine's configuration and the executor {@link ChangeStreamReader} runs it on,
 * one thread for the life of the application context — mirroring how {@code ReportClearingConfiguration} bounds
 * its own background pool.
 */
@Configuration
@EnableConfigurationProperties(CdcProperties.class)
public class ChangeStreamConfiguration {

    private static final String THREAD_NAME_PREFIX = "change-stream-";
    private static final String SLOT_MONITOR_THREAD_NAME_PREFIX = "slot-monitor-";

    private static final String CAPTURED_TABLES = "public.expense,public.expense_proposal,public.category";

    /** Moves the slot forward when nothing captured is written; never reaches the stream itself (F15). */
    private static final String HEARTBEAT_ACTION_QUERY = "UPDATE cdc_heartbeat SET beat_at = now()";

    @Bean(destroyMethod = "shutdown")
    Executor changeStreamExecutor() {
        return Executors.newSingleThreadExecutor(new CustomizableThreadFactory(THREAD_NAME_PREFIX));
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService slotMonitorExecutor() {
        return Executors.newSingleThreadScheduledExecutor(
                new CustomizableThreadFactory(SLOT_MONITOR_THREAD_NAME_PREFIX));
    }

    @Bean
    io.debezium.config.Configuration changeStreamEngineConfiguration(CdcProperties properties, DataSource dataSource) {
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        String jdbcUrl = hikariDataSource.getJdbcUrl();
        String user = hikariDataSource.getUsername();
        String password = hikariDataSource.getPassword();

        URI jdbcUri = URI.create(jdbcUrl.substring("jdbc:".length()));
        String databaseName = jdbcUri.getPath().substring(1);

        return io.debezium.config.Configuration.create()
                .with("name", properties.slotName())
                .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
                .with("topic.prefix", "ledger-service")
                // Required by Kafka Connect's WorkerConfig validation, which the embedded engine reuses even
                // though it never talks to a Kafka cluster.
                .with("bootstrap.servers", "localhost:9092")
                .with("database.hostname", jdbcUri.getHost())
                .with("database.port", jdbcUri.getPort())
                .with("database.dbname", databaseName)
                .with("database.user", user)
                .with("database.password", password)
                .with("plugin.name", "pgoutput")
                .with("slot.name", properties.slotName())
                .with("publication.name", "finance_ledger_cdc")
                .with("publication.autocreate.mode", "filtered")
                .with("table.include.list", CAPTURED_TABLES)
                .with("key.converter", "org.apache.kafka.connect.json.JsonConverter")
                .with("key.converter.schemas.enable", false)
                .with("value.converter", "org.apache.kafka.connect.json.JsonConverter")
                .with("value.converter.schemas.enable", false)
                .with("offset.storage", "io.debezium.storage.jdbc.offset.JdbcOffsetBackingStore")
                .with("offset.storage.jdbc.connection.url", jdbcUrl)
                .with("offset.storage.jdbc.connection.user", user)
                .with("offset.storage.jdbc.connection.password", password)
                .with("heartbeat.interval.ms", properties.heartbeatInterval().toMillis())
                .with("heartbeat.action.query", HEARTBEAT_ACTION_QUERY)
                .with("tombstones.on.delete", false)
                .with("snapshot.mode", properties.snapshotMode())
                .build();
    }
}
