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
                .waitingFor(Wait.forListeningPort());

        POSTGRES_CONTAINER.start();
    }

    private PostgresContainers() {}
}
