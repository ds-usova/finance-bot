package bot.finance.adapter.aiconnector;

import bot.finance.adapter.logging.TurnCallLog;
import bot.finance.adapter.mcp.CreateExpenseProposalToolRequest;
import bot.finance.adapter.mcp.ExpenseProposalToolMapper;
import bot.finance.adapter.security.AuthenticatedCaller;
import bot.finance.application.dto.SummarizeSpendingCommand;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.domain.exception.EntityNotFoundException;
import bot.finance.domain.exception.InvalidCategoryException;
import bot.finance.domain.exception.InvalidExpenseException;
import bot.finance.domain.exception.InvalidGroupingException;
import bot.finance.domain.exception.InvalidMoneyException;
import bot.finance.domain.exception.InvalidSpendingPeriodException;
import bot.finance.domain.exception.InvalidSpendingQueryException;
import bot.finance.domain.exception.InvalidUserException;
import bot.finance.domain.exception.PersistenceFailedException;
import bot.finance.domain.model.Expense;
import bot.finance.domain.value.AuthenticatedUserId;
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
            responseObserver.onError(fileExpenseRefusal(e));
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

    private StatusRuntimeException fileExpenseRefusal(RuntimeException e) {
        if (e instanceof InvalidMoneyException) {
            String field =
                    e.getMessage() != null && e.getMessage().contains("urrency code") ? "currency_code" : "amount";
            return refused(
                    CREATE_EXPENSE_PROPOSAL,
                    e,
                    Status.INVALID_ARGUMENT,
                    "invalid request: " + field + ": " + e.getMessage());
        }
        if (e instanceof InvalidExpenseException || e instanceof InvalidUserException) {
            return refused(CREATE_EXPENSE_PROPOSAL, e, Status.INVALID_ARGUMENT, "invalid request: " + e.getMessage());
        }
        if (e instanceof InvalidCategoryException || e instanceof InvalidGroupingException) {
            return refused(CREATE_EXPENSE_PROPOSAL, e, Status.INVALID_ARGUMENT, e.getMessage());
        }
        if (e instanceof EntityNotFoundException) {
            return refused(CREATE_EXPENSE_PROPOSAL, e, Status.NOT_FOUND, "the user is unknown");
        }
        if (e instanceof PersistenceFailedException) {
            return refused(CREATE_EXPENSE_PROPOSAL, e, Status.UNAVAILABLE, "the proposal could not be stored");
        }
        return refused(CREATE_EXPENSE_PROPOSAL, e, Status.INTERNAL, "the proposal could not be created");
    }

    private StatusRuntimeException recordPeriodRefusal(RuntimeException e) {
        if (e instanceof InvalidSpendingPeriodException) {
            return refused(SUMMARIZE_SPENDING, e, Status.INVALID_ARGUMENT, e.getMessage());
        }
        if (e instanceof InvalidSpendingQueryException || e instanceof InvalidUserException) {
            return refused(SUMMARIZE_SPENDING, e, Status.INVALID_ARGUMENT, "invalid request: " + e.getMessage());
        }
        if (e instanceof EntityNotFoundException) {
            return refused(SUMMARIZE_SPENDING, e, Status.NOT_FOUND, "the user is unknown");
        }
        if (e instanceof PersistenceFailedException) {
            return refused(SUMMARIZE_SPENDING, e, Status.UNAVAILABLE, "the summary could not be recorded");
        }
        return refused(SUMMARIZE_SPENDING, e, Status.INTERNAL, "the spending could not be summarized");
    }

    private StatusRuntimeException refused(String operation, RuntimeException e, Status status, String description) {
        log.warn("rejected {} call: {}", operation, e.getClass().getSimpleName());
        return status.withDescription(description).asRuntimeException();
    }
}
