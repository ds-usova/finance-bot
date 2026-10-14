package bot.finance.ai.application.port;

import bot.finance.ai.domain.value.ExtractionMode;
import java.time.Duration;

public interface TurnMeters {

    void recordTurn(ExtractionMode mode, boolean actedOn);

    void recordClassifierCall(Duration duration, boolean answered);

    void recordConfidence(double confidence);

    void countLowConfidence();
}
