package bot.finance.common;

import bot.finance.common.containers.PostgresContainers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Composed annotation for outbound persistence-adapter integration tests.
 *
 * <p>Boots the Data JDBC slice - Spring Data JDBC repositories, {@code JdbcTemplate}, the
 * transaction manager, and Flyway migrations - against the containerized Postgres singleton in
 * {@link PostgresContainers}. Nothing else is booted: no web layer, no security, no WireMock.
 *
 * <p>A test class adds {@code @Import(<AdapterUnderTest>.class)} and calls the adapter's own
 * public methods directly; nothing is mocked.
 *
 * <p>Each test runs in a transaction that is rolled back at the end (the slice default). Tests
 * that must verify committed state use {@code @Commit} and clean up after themselves.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ImportTestcontainers(PostgresContainers.class)
public @interface PersistenceAdapterTest {}
