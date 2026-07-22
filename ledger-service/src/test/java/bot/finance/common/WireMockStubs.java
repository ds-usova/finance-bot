package bot.finance.common;

import bot.finance.common.containers.WireMockSupport;

/**
 * Static helpers for registering WireMock stubs against external partner services.
 *
 * <p>The underlying {@link WireMockSupport#SERVER} is started once for the JVM and its stubs are
 * reset after every test via {@link AbstractIntegrationTest}'s {@code @AfterEach} hook.
 * Tests call one of these methods in their precondition setup; no lifecycle management is needed
 * inside the test class itself.
 *
 * <p>To add support for a new external service or endpoint, add a new static method here.
 */
public final class WireMockStubs {

}
