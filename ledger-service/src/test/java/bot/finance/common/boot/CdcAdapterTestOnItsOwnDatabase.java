package bot.finance.common.boot;

import bot.finance.common.containers.PostgresContainers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link CdcAdapterTest} for a class that brings its own database, declaring it with {@code @Container} and a
 * {@link DynamicPropertySource}. The two scenarios that need one are a {@code wal_level} below {@code logical}
 * and a missing publication, neither of which the shared {@link PostgresContainers} singleton can offer without
 * taking every other capture test down with it.
 *
 * <p>It is {@link CdcAdapterTest} without {@link OnTheContainerizedDatabase}. That role registers a service
 * connection, which outranks a property a class sets for itself — so a class using the shared annotation would
 * silently be handed the shared database and prove nothing about its own.
 *
 * <p>{@code Propagation.NOT_SUPPORTED} switches off the slice's rolled-back transaction. A capture test writes a
 * row and waits for the embedded engine, reading the write-ahead log on its own replication connection, to offer
 * it — and an uncommitted row never reaches that log. Each test therefore commits, and cleans up after itself.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TheDatabaseSlice
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(CaptureAdapterConfiguration.class)
public @interface CdcAdapterTestOnItsOwnDatabase {}
