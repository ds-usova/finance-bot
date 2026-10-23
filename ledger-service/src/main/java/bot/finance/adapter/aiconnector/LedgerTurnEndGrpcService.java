package bot.finance.adapter.aiconnector;

import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.application.dto.EndTurnCommand;
import bot.finance.application.port.EndTurnPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.ledger.adapter.grpc.v1.EndTurnRequest;
import bot.finance.ledger.adapter.grpc.v1.EndTurnResponse;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnEndServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class LedgerTurnEndGrpcService extends LedgerTurnEndServiceGrpc.LedgerTurnEndServiceImplBase {

    private final EndTurnPort endTurnPort;
    private final Logger log;

    public LedgerTurnEndGrpcService(EndTurnPort endTurnPort, LoggerFactory loggerFactory) {
        this.endTurnPort = endTurnPort;
        this.log = loggerFactory.getLogger(LedgerTurnEndGrpcService.class);
    }

    @Override
    public void endTurn(EndTurnRequest request, StreamObserver<EndTurnResponse> responseObserver) {
        try {
            endTurnPort.end(new EndTurnCommand(
                    AuthenticatedCaller.authenticatedUserId(),
                    AuthenticatedCaller.incomingMessageId(),
                    LedgerTurnProtoMapper.toEnding(request.getEnd())));

            responseObserver.onNext(EndTurnResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (PersistenceFailedException e) {
            log.warn("rejected end_turn call: {}", e.getClass().getSimpleName());
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription("the turn's end could not be stored")
                    .asRuntimeException());
        } catch (RuntimeException e) {
            log.warn("rejected end_turn call: {}", e.getClass().getSimpleName());
            responseObserver.onError(
                    Status.INTERNAL.withDescription("unexpected error").asRuntimeException());
        }
    }
}
