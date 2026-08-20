package bot.finance.ai.common.boot;

import bot.finance.ai.common.containers.RedisContainers;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Points a context at {@link RedisContainers}' singleton and gives it a stream key of its own. A registrar bean
 * rather than a {@code @DynamicPropertySource} method, the shape ledger-service's {@code CdcCaptureTest} uses: a
 * registrar bean is applied during context refresh, after any {@code @DynamicPropertySource} method a test
 * declares, so a test wanting a different Redis address imports its own registrar to win over this one. The
 * stream key is generated fresh per context, since two contexts sharing one key in one consumer group would
 * steal each other's entries.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisPropertiesConfiguration {

    @Bean
    DynamicPropertyRegistrar redisProperties() {
        String key = "ledger.cdc-" + UUID.randomUUID();
        return registry -> {
            registry.add("spring.data.redis.url", RedisContainers::redisUrl);
            registry.add("ledger.change-stream.key", () -> key);
        };
    }
}
