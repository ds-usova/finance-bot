package bot.finance.common.boot;

import bot.finance.common.containers.PostgresContainers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
@TheDatabaseSlice
@OnTheContainerizedDatabase
public @interface PersistenceAdapterTest {}
