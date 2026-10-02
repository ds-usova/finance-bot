package bot.finance.adapter.async;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

@Configuration
@EnableConfigurationProperties(WrittenMessageReaderProperties.class)
public class WrittenMessageReaderConfiguration {

    private static final String THREAD_NAME_PREFIX = "written-message-reader-";

    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService writtenMessageReaderExecutor() {
        return Executors.newSingleThreadScheduledExecutor(new CustomizableThreadFactory(THREAD_NAME_PREFIX));
    }
}
