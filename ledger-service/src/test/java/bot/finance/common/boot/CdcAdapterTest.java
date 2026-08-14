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
 * <p>Boots the Data JDBC slice against the containerized Postgres, plus the capture adapter's beans and a Redis
 * template pointed at the real Redis. Nothing else: no web layer, no Telegram poll loop, no MCP server, no gRPC
 * client, no security chains. A test that drives the change stream through the application's own entry points
 * takes {@link CdcCaptureTest} instead; one that needs a database of its own takes
 * {@link CdcAdapterTestOnItsOwnDatabase}.
 *
 * <p>{@code Propagation.NOT_SUPPORTED} switches off the slice's rolled-back transaction. A capture test writes a
 * row and waits for the embedded engine, reading the write-ahead log on its own replication connection, to offer
 * it — and an uncommitted row never reaches that log. Each test therefore commits, and cleans up after itself.
 *
 * <p>The slot name is left at the application's own default. Two capture classes opening one slot against the
 * shared {@link PostgresContainers} singleton fight over it, so a class declares its own
 * {@code @TestPropertySource(properties = "cdc.slot-name=...")}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@CdcAdapterTestOnItsOwnDatabase
@OnTheContainerizedDatabase
public @interface CdcAdapterTest {}
