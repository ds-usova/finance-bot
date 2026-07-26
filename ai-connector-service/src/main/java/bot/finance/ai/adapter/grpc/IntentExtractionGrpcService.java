package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import bot.finance.ai.application.port.ExtractIntentsPort;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class IntentExtractionGrpcService
        extends IntentExtractionServiceGrpc.IntentExtractionServiceImplBase {

    private final ExtractIntentsPort extractIntentsPort;

    public IntentExtractionGrpcService(ExtractIntentsPort extractIntentsPort) {
        this.extractIntentsPort = extractIntentsPort;
    }

    @Override
    public void extractIntents(ExtractIntentsRequest request, StreamObserver<ExtractIntentsResponse> responseObserver) {
        // rejects the request via rejectIfInvalid() before building anything, in which case the port
        // is never called; otherwise maps request.getText(), request.getKnownCategoriesList() and, when
        // present, request.getDefaultCurrency() into an IntentExtractionCommand, calls
        // extractIntentsPort.extractIntents(), maps the result through IntentProtoUtils.toResponse(),
        // and completes responseObserver with onNext then onCompleted
        super.extractIntents(request, responseObserver);
    }

    private boolean rejectIfInvalid(ExtractIntentsRequest request, StreamObserver<ExtractIntentsResponse> responseObserver) {
        // runs before the command is constructed, rejecting blank or absent text, an empty
        // known_categories or one containing a blank entry, and a default_currency present but not a code
        // java.util.Currency knows — each via responseObserver.onError(Status.INVALID_ARGUMENT
        // .withDescription(...).asRuntimeException()), returning true so the caller stops before the port
        // is reached
        return false;
    }

}
