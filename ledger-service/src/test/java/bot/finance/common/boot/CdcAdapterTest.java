package bot.finance.common.boot;

import bot.finance.common.containers.PostgresContainers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composed annotation for the change-capture adapter's own integration tests.
 *
 * <p>A test that drives the change stream through the application's own entry points takes
 * {@link CdcCaptureTest} instead; one that needs a database of its own takes
 * {@link CdcAdapterTestOnItsOwnDatabase}.
 *
 * <p>The slot name is left at the application's own default. Two capture classes opening one slot against the
 * {@link PostgresContainers} singleton fight over it, so a class declares its own
 * {@code @TestPropertySource(properties = "cdc.slot-name=...")}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@TheCaptureAdapter
@OnTheContainerizedDatabase
public @interface CdcAdapterTest {}
