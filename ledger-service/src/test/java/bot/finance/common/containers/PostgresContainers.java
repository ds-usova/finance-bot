package bot.finance.common.containers;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class PostgresContainers {

    private static final String POSTGRES_IMAGE = "postgres:18";

    // Spring Boot derives every datasource property (URL, username, password) from this container
    // automatically in any test context that imports this class via
    // @ImportTestcontainers(PostgresContainers.class) - no @DynamicPropertySource or hand-written
    // property strings needed.
    @ServiceConnection
    public static final PostgreSQLContainer<?> POSTGRES_CONTAINER;

    static {
        POSTGRES_CONTAINER = new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withNetwork(Network.NETWORK)
                .withNetworkAliases("ledger_db")
                .withDatabaseName("ledger_db")
                .withUsername("ledger_user")
                .withPassword("ledger_password")
                .withReuse(false)
                // Every cached Spring context in the test JVM holds a pool against this one container, and the
                // capture tests add a replication connection each, so the server's default 100 slots are the
                // suite's real ceiling rather than a comfortable bound.
                .withCommand(
                        "postgres",
                        "-c",
                        "wal_level=logical",
                        "-c",
                        "max_slot_wal_keep_size=16MB",
                        "-c",
                        "max_connections=300")
                .waitingFor(Wait.forListeningPort());

        POSTGRES_CONTAINER.start();
    }

    private PostgresContainers() {}
}
