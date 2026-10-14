package bot.finance.ai.adapter.config;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("extraction")
public record ExtractionProperties(String mode, int maxTurns, Duration turnTimeout) {

    private static final String TOOLS = "tools";
    private static final Set<String> MODES = Set.of(TOOLS, "classifier");

    public ExtractionProperties {
        if (mode == null) {
            mode = TOOLS;
        }
        if (!MODES.contains(mode)) {
            throw new IllegalArgumentException("EXTRACTION_MODE must be tools or classifier, but was '" + mode + "'");
        }
        if (maxTurns <= 0) {
            throw new IllegalArgumentException("EXTRACTION_MAX_TURNS must be positive, but was " + maxTurns);
        }
        if (turnTimeout == null || turnTimeout.isZero() || turnTimeout.isNegative()) {
            throw new IllegalArgumentException("EXTRACTION_TURN_TIMEOUT must be positive, but was " + turnTimeout);
        }
    }
}
