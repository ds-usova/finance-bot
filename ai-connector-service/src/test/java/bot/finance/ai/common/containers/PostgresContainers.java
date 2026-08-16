package bot.finance.ai.common.containers;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class PostgresContainers {

    private static final DockerImageName PGVECTOR_IMAGE =
            DockerImageName.parse("pgvector/pgvector:pg18").asCompatibleSubstituteFor("postgres");

    // Spring Boot derives every datasource property (URL, username, password) from this container
    // automatically in any test context that imports this class via
    // @ImportTestcontainers(PostgresContainers.class) - no @DynamicPropertySource or hand-written
    // property strings needed.
    @ServiceConnection
    public static final PostgreSQLContainer<?> POSTGRES_CONTAINER;

    static {
        POSTGRES_CONTAINER = new PostgreSQLContainer<>(PGVECTOR_IMAGE)
                .withDatabaseName("finance_ai_test")
                .withUsername("finance_ai_test")
                .withPassword("finance_ai_test")
                .withInitScript("db/init/create-extension.sql")
                .withReuse(false)
                .waitingFor(Wait.forListeningPort());

        POSTGRES_CONTAINER.start();
    }

    private PostgresContainers() {}
}
