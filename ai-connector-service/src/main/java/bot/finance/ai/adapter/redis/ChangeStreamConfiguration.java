package bot.finance.ai.adapter.redis;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
@EnableConfigurationProperties(ChangeStreamProperties.class)
public class ChangeStreamConfiguration {

    @Bean
    StringRedisTemplate changeStreamRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService changeStreamExecutor() {
        return Executors.newSingleThreadExecutor();
    }
}
