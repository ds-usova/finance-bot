package bot.finance.common.boot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * Spring Data JDBC repositories, {@code JdbcTemplate}, the transaction manager and Flyway — against the real
 * database the context is configured with, never an in-memory replacement. Which database that is comes from
 * {@link OnTheContainerizedDatabase} or from the class's own container.
 *
 * <p>Each test runs in a transaction the slice rolls back. A class that needs its writes committed says so
 * itself, and says why.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@DataJdbcTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
public @interface TheDatabaseSlice {}
