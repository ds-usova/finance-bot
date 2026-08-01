package bot.finance.adapter.aiconnector;

import bot.finance.adapter.security.AccessTokenMinter;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.IntentExtractionFailedException;
import bot.finance.domain.exception.InvalidExtractionRequestException;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

@Component
public class AiConnectorIntentExtractionAdapter implements IntentExtractionPort {

    private final IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub intentExtractionStub;
    private final AccessTokenMinter accessTokenMinter;
    private final Logger log;

    public AiConnectorIntentExtractionAdapter(
            IntentExtractionServiceGrpc.IntentExtractionServiceBlockingStub intentExtractionStub,
            AccessTokenMinter accessTokenMinter,
            LoggerFactory loggerFactory) {
        this.intentExtractionStub = intentExtractionStub;
        this.accessTokenMinter = accessTokenMinter;
        this.log = loggerFactory.getLogger(AiConnectorIntentExtractionAdapter.class);
    }

    @Override
    public void extract(IntentExtractionRequest request) {
        if (request == null) {
            throw new InvalidExtractionRequestException("Intent extraction request must not be null");
        }

        log.debug("Extracting the intent with text {}", request.text());

        // TODO: mint a token for request.userExternalId() through accessTokenMinter and attach it to the stub
        // call as `authorization: Bearer <jwt>` metadata before invoking extractIntents.
        ExtractIntentsRequest protoRequest = IntentProtoUtils.toProtoRequest(request);
        try {
            intentExtractionStub.extractIntents(protoRequest);
        } catch (StatusRuntimeException e) {
            log.error("Failed to extract the intent:", e);
            throw new IntentExtractionFailedException(
                    "Intent extraction call failed with status "
                            + e.getStatus().getCode().name(),
                    e);
        }
    }
}
