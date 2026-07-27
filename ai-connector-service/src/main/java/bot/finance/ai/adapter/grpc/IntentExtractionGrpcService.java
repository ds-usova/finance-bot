package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import bot.finance.ai.application.dto.IntentExtractionCommand;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.domain.value.CurrencyCode;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Currency;
import java.util.Optional;

@GrpcService
public class IntentExtractionGrpcService
        extends IntentExtractionServiceGrpc.IntentExtractionServiceImplBase {

    private final ExtractIntentsPort extractIntentsPort;

    public IntentExtractionGrpcService(ExtractIntentsPort extractIntentsPort) {
        this.extractIntentsPort = extractIntentsPort;
    }

    @Override
    public void extractIntents(
            ExtractIntentsRequest request, StreamObserver<ExtractIntentsResponse> responseObserver) {
        if (rejectIfInvalid(request, responseObserver)) {
            return;
        }

        Optional<CurrencyCode> defaultCurrency = request.hasDefaultCurrency()
                ? Optional.of(CurrencyCode.of(request.getDefaultCurrency()))
                : Optional.empty();

        IntentExtractionCommand command = new IntentExtractionCommand(
                request.getText(), request.getKnownCategoriesList(), defaultCurrency);
        ExtractIntentsResponse response = IntentProtoUtils.toResponse(extractIntentsPort.extractIntents(command));

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private boolean rejectIfInvalid(
            ExtractIntentsRequest request, StreamObserver<ExtractIntentsResponse> responseObserver) {
        if (request.getText().isBlank()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription("Text must not be blank").asRuntimeException());
            return true;
        }

        if (request.getKnownCategoriesList().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Known categories must not be empty")
                    .asRuntimeException());
            return true;
        }

        if (request.getKnownCategoriesList().stream().anyMatch(String::isBlank)) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Known categories must not contain a blank entry")
                    .asRuntimeException());
            return true;
        }

        if (request.hasDefaultCurrency() && !isKnownCurrency(request.getDefaultCurrency())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Unrecognized ISO 4217 currency code: " + request.getDefaultCurrency())
                    .asRuntimeException());
            return true;
        }

        return false;
    }

    private boolean isKnownCurrency(String currencyCode) {
        try {
            Currency.getInstance(currencyCode);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
