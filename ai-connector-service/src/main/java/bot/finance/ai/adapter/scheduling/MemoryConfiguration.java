package bot.finance.ai.adapter.scheduling;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService memoryPurgeExecutor() {
        return Executors.newSingleThreadScheduledExecutor();
    }
}
