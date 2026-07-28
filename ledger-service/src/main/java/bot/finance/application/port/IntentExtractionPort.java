package bot.finance.application.port;

import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.Intent;
import java.util.List;

public interface IntentExtractionPort {

    /**
     * @throws InvalidExtractionRequestException if the request is absent
     * @throws IntentExtractionFailedException if the call to the AI connector fails or its answer is unusable
     */
    List<Intent> extract(IntentExtractionRequest request);
}
