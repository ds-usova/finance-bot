package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.domain.exception.InvalidValueException;
import bot.finance.ai.domain.value.CurrencyCode;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
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

        ExtractIntentsCommand command = new ExtractIntentsCommand(
                request.getText(), request.getCategoryGroupingsList(), request.getCatchAllGrouping(), defaultCurrency);
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

        // TODO RI05: reject empty category_groupings, a blank entry in category_groupings, a blank
        // catch_all_grouping, and a catch_all_grouping not among category_groupings — each INVALID_ARGUMENT

        return false;
    }
}
