package bot.finance.ai.common.boot;

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

import bot.finance.ai.common.containers.PostgresContainers;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Wires only the persistence slice against the real, containerized database — {@code replace = NONE} keeps
 * {@link PostgresContainers}' connection instead of an embedded one. A test class adds
 * {@code @Import(<AdapterUnderTest>.class)} for the adapter it drives. Skips when Docker is down.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJdbcTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "memory.enabled=true")
@Testcontainers(disabledWithoutDocker = true)
@ImportTestcontainers(PostgresContainers.class)
public @interface PersistenceAdapterTest {}
