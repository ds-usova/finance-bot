package bot.finance.common.boot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The change-capture adapter's beans on {@link TheDatabaseSlice}, and a Redis template. Which database it runs
 * against is the consumer's to say.
 *
 * <p>{@code Propagation.NOT_SUPPORTED} switches off the slice's rolled-back transaction. A capture test writes a
 * row and waits for the embedded engine, reading the write-ahead log on its own replication connection, to offer
 * it — and an uncommitted row never reaches that log. Each test therefore commits, and cleans up after itself.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ActiveProfiles("test")
@TheDatabaseSlice
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(CaptureAdapterConfiguration.class)
public @interface TheCaptureAdapter {}
