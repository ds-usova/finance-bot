package bot.finance.adapter.aiconnector;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidExtractionRequestException;
import bot.finance.domain.value.Intent;
import io.grpc.StatusRuntimeException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AiConnectorIntentExtractionAdapter implements IntentExtractionPort {

    private final IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub intentExtractionStub;
    private final Logger log;

    public AiConnectorIntentExtractionAdapter(
            IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub intentExtractionStub,
            LoggerFactory loggerFactory) {
        this.intentExtractionStub = intentExtractionStub;
        this.log = loggerFactory.getLogger(AiConnectorIntentExtractionAdapter.class);
    }

    @Override
    public List<Intent> extract(IntentExtractionRequest request) {
        if (request == null) {
            throw new InvalidExtractionRequestException("Intent extraction request must not be null");
        }

        ExtractIntentsRequest protoRequest = IntentProtoUtils.toProtoRequest(request);
        ExtractIntentsResponse protoResponse;
        try {
            log.debug("calling IntentExtractionService/ExtractIntents");
            protoResponse = intentExtractionStub.extractIntents(protoRequest);
        } catch (StatusRuntimeException e) {
            throw new IntentExtractionFailedException(
                    "Intent extraction call failed with status "
                            + e.getStatus().getCode().name(),
                    e);
        }

        List<Intent> intents = IntentProtoUtils.toIntents(protoResponse);
        if (intents.isEmpty()) {
            throw new IntentExtractionFailedException("Intent extraction response carried no entries", null);
        }
        return intents;
    }
}
