package bot.finance.adapter.async;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

@Configuration
@EnableConfigurationProperties(ReportClearingProperties.class)
public class ReportClearingConfiguration {

    private static final String THREAD_NAME_PREFIX = "report-clearing-";
    private static final long KEEP_ALIVE_SECONDS = 60L;

    @Bean
    Executor reportClearingExecutor(ReportClearingProperties properties) {
        // a pool with no room drops the work rather than blocking or running it on the caller's thread
        return new ThreadPoolExecutor(
                properties.coreSize(),
                properties.maxSize(),
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()),
                new CustomizableThreadFactory(THREAD_NAME_PREFIX),
                new ThreadPoolExecutor.DiscardPolicy());
    }
}
