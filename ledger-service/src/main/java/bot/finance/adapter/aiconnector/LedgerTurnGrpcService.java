package bot.finance.adapter.aiconnector;

import bot.finance.adapter.logging.TurnCallLog;
import bot.finance.adapter.mcp.CreateExpenseProposalToolRequest;
import bot.finance.adapter.mcp.ExpenseProposalToolMapper;
import bot.finance.adapter.mcp.ToolRefusal;
import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.application.dto.SummarizeSpendingCommand;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.CurrencyCode;
import bot.finance.domain.value.IncomingMessageId;
import bot.finance.domain.value.SpendingPeriod;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalResponse;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnServiceGrpc;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodRequest;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.Map;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class LedgerTurnGrpcService extends LedgerTurnServiceGrpc.LedgerTurnServiceImplBase {

    private static final String CREATE_EXPENSE_PROPOSAL = "create_expense_proposal";
    private static final String SUMMARIZE_SPENDING = "summarize_spending";

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
        CreateExpenseProposalToolRequest toolRequest = LedgerTurnProtoMapper.toToolRequest(request);

        try {
            AuthenticatedUserId userId = AuthenticatedCaller.authenticatedUserId();
            IncomingMessageId reference = AuthenticatedCaller.incomingMessageId();

            Expense stored = createExpenseProposalPort.create(
                    ExpenseProposalToolMapper.toCommand(toolRequest, userId, reference));

            turnCallLog.write(CREATE_EXPENSE_PROPOSAL, TurnCallLog.Via.GRPC, true, toolRequest);
            responseObserver.onNext(
                    LedgerTurnProtoMapper.toResponse(stored, toolRequest.grouping(), toolRequest.category()));
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            turnCallLog.write(CREATE_EXPENSE_PROPOSAL, TurnCallLog.Via.GRPC, false, toolRequest);
            responseObserver.onError(fileExpenseRefusal(e, toolRequest.currencyCode()));
        }
    }

    @Override
    public void recordSpendingPeriod(
            RecordSpendingPeriodRequest request, StreamObserver<RecordSpendingPeriodResponse> responseObserver) {
        Map<String, String> arguments = TurnCallLog.arguments("from", request.getFrom(), "to", request.getTo());

        try {
            AuthenticatedUserId userId = AuthenticatedCaller.authenticatedUserId();
            IncomingMessageId reference = AuthenticatedCaller.incomingMessageId();

            SpendingPeriod period = summarizeSpendingPort.summarize(
                    new SummarizeSpendingCommand(userId, reference, request.getFrom(), request.getTo()));

            turnCallLog.write(SUMMARIZE_SPENDING, TurnCallLog.Via.GRPC, true, arguments);
            responseObserver.onNext(LedgerTurnProtoMapper.toResponse(period));
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            turnCallLog.write(SUMMARIZE_SPENDING, TurnCallLog.Via.GRPC, false, arguments);
            responseObserver.onError(recordPeriodRefusal(e));
        }
    }

    private StatusRuntimeException fileExpenseRefusal(RuntimeException e, String currencyCode) {
        ToolRefusal refusal = ToolRefusal.ofExpenseProposal(e);

        if (e instanceof InvalidMoneyException) {
            String field = recordsAmounts(currencyCode) ? "amount" : "currency_code";
            return refused(
                    CREATE_EXPENSE_PROPOSAL, e, refusal.kind(), "invalid request: " + field + ": " + e.getMessage());
        }
        return refused(CREATE_EXPENSE_PROPOSAL, e, refusal.kind(), refusal.message());
    }

    private static boolean recordsAmounts(String currencyCode) {
        try {
            return CurrencyCode.of(currencyCode).recordsAmounts();
        } catch (InvalidMoneyException e) {
            return false;
        }
    }

    private StatusRuntimeException recordPeriodRefusal(RuntimeException e) {
        ToolRefusal refusal = ToolRefusal.ofSpendingSummary(e);
        return refused(SUMMARIZE_SPENDING, e, refusal.kind(), refusal.message());
    }

    private StatusRuntimeException refused(
            String operation, RuntimeException e, ToolRefusal.Kind kind, String description) {
        log.warn("rejected {} call: {}", operation, e.getClass().getSimpleName());
        return statusOf(kind).withDescription(description).asRuntimeException();
    }

    private static Status statusOf(ToolRefusal.Kind kind) {
        return switch (kind) {
            case INVALID_REQUEST -> Status.INVALID_ARGUMENT;
            case UNKNOWN_USER -> Status.NOT_FOUND;
            case STORE_FAILED -> Status.UNAVAILABLE;
            case UNEXPECTED -> Status.INTERNAL;
        };
    }
}
