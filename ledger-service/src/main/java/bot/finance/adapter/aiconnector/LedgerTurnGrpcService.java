package bot.finance.adapter.aiconnector;

import bot.finance.adapter.logging.TurnCallLog;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalResponse;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnServiceGrpc;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodRequest;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodResponse;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class LedgerTurnGrpcService extends LedgerTurnServiceGrpc.LedgerTurnServiceImplBase {

    private final CreateExpenseProposalPort createExpenseProposalPort;
    private final SummarizeSpendingPort summarizeSpendingPort;
    private final TurnCallLog turnCallLog;
    private final Logger log;

    public LedgerTurnGrpcService(
            CreateExpenseProposalPort createExpenseProposalPort,
            SummarizeSpendingPort summarizeSpendingPort,
            TurnCallLog turnCallLog,
            LoggerFactory loggerFactory) {
        this.createExpenseProposalPort = createExpenseProposalPort;
        this.summarizeSpendingPort = summarizeSpendingPort;
        this.turnCallLog = turnCallLog;
        this.log = loggerFactory.getLogger(LedgerTurnGrpcService.class);
    }

    @Override
    public void fileExpenseProposal(
            FileExpenseProposalRequest request, StreamObserver<FileExpenseProposalResponse> responseObserver) {
        // stub-intent: files a pending expense as the authenticated caller for its message, answers the stored
        // entry, maps each refusal to its status with the tool's reason, logs each refusal with its kind and
        // never the request or token, and writes the call line as create_expense_proposal
        responseObserver.onNext(FileExpenseProposalResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void recordSpendingPeriod(
            RecordSpendingPeriodRequest request, StreamObserver<RecordSpendingPeriodResponse> responseObserver) {
        // stub-intent: records the period as the authenticated caller for its message, answers its two days,
        // maps each refusal to its status with the tool's reason, logs each refusal with its kind and never the
        // request or token, and writes the call line as summarize_spending
        responseObserver.onNext(RecordSpendingPeriodResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
