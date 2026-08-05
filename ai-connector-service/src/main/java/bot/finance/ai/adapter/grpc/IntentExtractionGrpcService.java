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
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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

        LocalDate currentDate = LocalDate.parse(request.getCurrentDate());
        ExtractIntentsCommand command = new ExtractIntentsCommand(
                request.getText(),
                request.getCategoryGroupingsList(),
                request.getCatchAllGrouping(),
                defaultCurrency,
                currentDate);
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

        if (request.getCategoryGroupingsList().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Category groupings must not be empty")
                    .asRuntimeException());
            return true;
        }

        if (request.getCategoryGroupingsList().stream().anyMatch(String::isBlank)) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Category groupings must not contain a blank name")
                    .asRuntimeException());
            return true;
        }

        if (request.getCatchAllGrouping().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Catch-all grouping must not be blank")
                    .asRuntimeException());
            return true;
        }

        if (!request.getCategoryGroupingsList().contains(request.getCatchAllGrouping())) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Catch-all grouping must be one of the category groupings")
                    .asRuntimeException());
            return true;
        }

        if (request.getCurrentDate().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Current date must not be blank")
                    .asRuntimeException());
            return true;
        }

        try {
            LocalDate.parse(request.getCurrentDate());
        } catch (DateTimeParseException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Current date must be an ISO-8601 date")
                    .asRuntimeException());
            return true;
        }

        return false;
    }
}
