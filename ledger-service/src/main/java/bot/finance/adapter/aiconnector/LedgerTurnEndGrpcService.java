package bot.finance.adapter.aiconnector;

import bot.finance.application.port.EndTurnPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.ledger.adapter.grpc.v1.EndTurnRequest;
import bot.finance.ledger.adapter.grpc.v1.EndTurnResponse;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnEndServiceGrpc;
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
        // stub-intent: ends the authenticated caller's turn for the message its credential names, with the ending
        // the request carries; answers empty once taken, maps a store failure to UNAVAILABLE and anything else to
        // INTERNAL naming nothing internal, and writes no turn call line
        responseObserver.onNext(EndTurnResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
