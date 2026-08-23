package bot.finance.common.boot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composed annotation for outbound persistence-adapter integration tests.
 *
 * <p>A test class adds {@code @Import(<AdapterUnderTest>.class)} and calls the adapter's own public methods
 * directly; nothing is mocked. A test that must verify committed state uses {@code @Commit} and cleans up after
 * itself.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@TheDatabaseSlice
@OnTheContainerizedDatabase
public @interface PersistenceAdapterTest {}
