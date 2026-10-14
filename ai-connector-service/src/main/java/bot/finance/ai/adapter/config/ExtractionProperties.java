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
        // TODO: check maxTurns is positive, naming EXTRACTION_MAX_TURNS, and turnTimeout is non-null and positive,
        // naming EXTRACTION_TURN_TIMEOUT
    }
}
