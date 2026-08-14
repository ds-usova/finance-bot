package bot.finance.common.boot;

import bot.finance.common.containers.PostgresContainers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link TheCaptureAdapter} against a database the test class declares itself, as a {@code @Container} field and
 * a {@code @DynamicPropertySource}. The two scenarios that need one are a {@code wal_level} below
 * {@code logical} and a missing publication, neither of which the shared {@link PostgresContainers} singleton
 * can offer without taking every other capture test down with it.
 *
 * <p>{@link OnTheContainerizedDatabase} is what a class must not use for this: it registers a service
 * connection, which outranks a property a class sets for itself, so the class would silently be handed the
 * shared database and prove nothing about its own.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@TheCaptureAdapter
@Testcontainers(disabledWithoutDocker = true)
public @interface CdcAdapterTestOnItsOwnDatabase {}
