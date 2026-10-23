package bot.finance.application.port;

import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.IntentExtractionRefusedException;
import bot.finance.domain.exception.InvalidExtractionRequestException;

public interface IntentExtractionPort {

    /**
     * @throws InvalidExtractionRequestException if the request is absent
     * @throws IntentExtractionRefusedException if the connector refuses the request itself
     * @throws IntentExtractionFailedException when the hand-over is not accepted
     */
    void extract(IntentExtractionRequest request);
}
