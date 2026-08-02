package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.KnownCategory;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Optional;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class IntentExtractionGrpcService extends IntentExtractionServiceGrpc.IntentExtractionServiceImplBase {

    private final ExtractIntentsPort extractIntentsPort;

    public IntentExtractionGrpcService(ExtractIntentsPort extractIntentsPort) {
        this.extractIntentsPort = extractIntentsPort;
    }

    @Override
    public void extractIntents(ExtractIntentsRequest request, StreamObserver<ExtractIntentsResponse> responseObserver) {
        if (rejectIfInvalid(request, responseObserver)) {
            return;
        }

        Optional<CurrencyCode> defaultCurrency;
        try {
            defaultCurrency = request.hasDefaultCurrency()
                    ? Optional.of(CurrencyCode.of(request.getDefaultCurrency()))
                    : Optional.empty();
        } catch (InvalidValueException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Unrecognized ISO 4217 currency code: " + request.getDefaultCurrency())
                    .asRuntimeException());
            return;
        }

        List<KnownCategory> knownCategories = request.getKnownCategoriesList().stream()
                .map(entry -> new KnownCategory(entry.getName(), entry.getParentName()))
                .toList();
        ExtractIntentsCommand command = new ExtractIntentsCommand(request.getText(), knownCategories, defaultCurrency);
        extractIntentsPort.extractIntents(command);

        responseObserver.onNext(ExtractIntentsResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private boolean rejectIfInvalid(
            ExtractIntentsRequest request, StreamObserver<ExtractIntentsResponse> responseObserver) {
        if (request.getText().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Text must not be blank")
                    .asRuntimeException());
            return true;
        }

        if (request.getKnownCategoriesList().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Known categories must not be empty")
                    .asRuntimeException());
            return true;
        }

        boolean hasBlankCategory = request.getKnownCategoriesList().stream()
                .anyMatch(entry ->
                        entry.getName().isBlank() || entry.getParentName().isBlank());
        if (hasBlankCategory) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Known category name and parent_name must not be blank")
                    .asRuntimeException());
            return true;
        }

        return false;
    }
}
