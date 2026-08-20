package bot.finance.ai.common.boot;

import bot.finance.ai.adapter.metrics.MicrometerChangeStreamMeters;
import bot.finance.ai.adapter.metrics.MicrometerRecallMeters;
import bot.finance.ai.application.port.PendingEntryCountPort;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots only the metrics adapter — {@link MicrometerRecallMeters} and {@link MicrometerChangeStreamMeters} —
 * with autoconfiguration on over a random port, so the actuator's prometheus endpoint renders their meters for
 * a scrape test to read. {@code memory.enabled=true} switches both beans on; the datasource, Redis and the
 * provider are excluded, and {@link PendingEntryCountPort} is a Mockito mock for the pending gauge to sample.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
@SpringBootTest(
        classes = MetricsAdapterTest.MetricsAdapterConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "memory.enabled=true",
            "spring.grpc.server.port=0",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                    + "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,"
                    + "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,"
                    + "org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration,"
                    + "org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration,"
                    + "org.springframework.ai.mcp.client.httpclient.autoconfigure.StreamableHttpHttpClientTransportAutoConfiguration"
        })
public @interface MetricsAdapterTest {

    @EnableAutoConfiguration
    @Import({MicrometerRecallMeters.class, MicrometerChangeStreamMeters.class})
    class MetricsAdapterConfiguration {

        @Bean
        PendingEntryCountPort pendingEntryCountPort() {
            return Mockito.mock(PendingEntryCountPort.class);
        }
    }
}
