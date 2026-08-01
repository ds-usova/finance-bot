package bot.finance.application.port;

import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidExtractionRequestException;

public interface IntentExtractionPort {

    /**
     * @throws InvalidExtractionRequestException if the request is absent
     * @throws IntentExtractionFailedException when the turn does not complete
     */
    void extract(IntentExtractionRequest request);
}
