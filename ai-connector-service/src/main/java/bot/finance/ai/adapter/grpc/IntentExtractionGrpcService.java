package bot.finance.ai.adapter.grpc;

import bot.finance.ai.adapter.grpc.v1.ExtractIntentsRequest;
import bot.finance.ai.adapter.grpc.v1.ExtractIntentsResponse;
import bot.finance.ai.adapter.grpc.v1.IntentExtractionServiceGrpc;
import bot.finance.ai.application.dto.AcceptTurnCommand;
import bot.finance.ai.application.dto.ExtractIntentsCommand;
import bot.finance.ai.application.dto.TurnAcceptance;
import bot.finance.ai.application.port.AcceptTurnPort;
import bot.finance.ai.domain.exception.InvalidValueException;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class IntentExtractionGrpcService extends IntentExtractionServiceGrpc.IntentExtractionServiceImplBase {

    private final AcceptTurnPort acceptTurnPort;

    public IntentExtractionGrpcService(AcceptTurnPort acceptTurnPort) {
        this.acceptTurnPort = acceptTurnPort;
    }

    @Override
    public void extractIntents(ExtractIntentsRequest request, StreamObserver<ExtractIntentsResponse> responseObserver) {
        AcceptTurnCommand command;
        try {
            ExtractIntentsCommand turn =
                    ExtractIntentsRequestReader.toCommand(request, CallerTokenContext.messageIdentity());
            command = new AcceptTurnCommand(turn, CallerTokenContext.turnKey().orElse(null));
        } catch (InvalidValueException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
            return;
        }

        TurnAcceptance acceptance = acceptTurnPort.accept(command);

        if (acceptance == TurnAcceptance.BUSY) {
            responseObserver.onError(Status.RESOURCE_EXHAUSTED
                    .withDescription("a turn is already running")
                    .asRuntimeException());
            return;
        }

        responseObserver.onNext(ExtractIntentsResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
