package bot.finance.adapter.aiconnector;

import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import bot.finance.application.dto.IntentExtractionRequest;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.value.Intent;

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
        // TODO: GI01 rejects an absent request with InvalidExtractionRequestException before calling the
        // stub; otherwise maps the request with IntentProtoUtils.toProtoRequest, calls the connector's
        // IntentExtractionService/ExtractIntents, maps the response with IntentProtoUtils.toIntents, and
        // translates every StatusRuntimeException and an empty answer into
        // IntentExtractionFailedException.
        return List.of();
    }
}
