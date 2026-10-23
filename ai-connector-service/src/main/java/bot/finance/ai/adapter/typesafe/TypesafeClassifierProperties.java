package bot.finance.ai.adapter.typesafe;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("classifier")
public record TypesafeClassifierProperties(
        String apiKey, URI baseUrl, String model, Duration timeout, double minConfidence) {

    public TypesafeClassifierProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("TYPESAFE_API_KEY must be set");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("CLASSIFIER_MODEL must be set");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("CLASSIFIER_TIMEOUT must be positive, but was " + timeout);
        }
        if (!(minConfidence >= 0 && minConfidence <= 1)) {
            throw new IllegalArgumentException(
                    "CLASSIFIER_MIN_CONFIDENCE must be between 0 and 1, but was " + minConfidence);
        }
    }
}
