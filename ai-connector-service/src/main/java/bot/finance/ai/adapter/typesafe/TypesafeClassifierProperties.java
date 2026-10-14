package bot.finance.ai.adapter.typesafe;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("classifier")
public record TypesafeClassifierProperties(
        String apiKey, URI baseUrl, String model, Duration timeout, double minConfidence) {}
