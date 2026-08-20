package bot.finance.common.boot;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The meter registry a slice does not carry. An adapter that records a meter needs one to construct at all, and a
 * registry nothing scrapes is inert, so {@link PersistenceAdapterTest} supplies one to every persistence test
 * rather than each class that happens to reach a metered adapter importing its own.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InMemoryMeters {

    @Bean
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
