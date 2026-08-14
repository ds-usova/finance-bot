package bot.finance.common.boot;

import bot.finance.common.containers.PostgresContainers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
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
 * <p>It is {@link CdcAdapterTest} without {@code @ImportTestcontainers(PostgresContainers.class)}. That import
 * registers a service connection, which outranks a property a class sets for itself — so a class using the
 * shared annotation would silently be handed the shared database and prove nothing about its own.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DataJdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(CaptureAdapterConfiguration.class)
public @interface CdcAdapterTestOnItsOwnDatabase {}
