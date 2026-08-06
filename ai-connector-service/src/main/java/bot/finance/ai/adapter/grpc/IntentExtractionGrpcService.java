package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.domain.exception.InvalidValueException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class IntentExtractionGrpcService extends IntentExtractionServiceGrpc.IntentExtractionServiceImplBase {

    private final ExtractIntentsPort extractIntentsPort;

    public IntentExtractionGrpcService(ExtractIntentsPort extractIntentsPort) {
        this.extractIntentsPort = extractIntentsPort;
    }

    @Override
    public void extractIntents(ExtractIntentsRequest request, StreamObserver<ExtractIntentsResponse> responseObserver) {
        ExtractIntentsCommand command;
        try {
            command = ExtractIntentsRequestReader.toCommand(request);
        } catch (InvalidValueException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            return;
        }

        extractIntentsPort.extractIntents(command);

        responseObserver.onNext(ExtractIntentsResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
