package bot.finance.ai.adapter.ledger;

import bot.finance.ai.application.port.LedgerTurnPort;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.exception.LedgerCallRefusedException;
import bot.finance.ai.domain.value.CategoryChoice;
import bot.finance.ai.domain.value.ExpenseToFile;
import bot.finance.ledger.adapter.grpc.v1.FileExpenseProposalRequest;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnServiceGrpc;
import bot.finance.ledger.adapter.grpc.v1.RecordSpendingPeriodRequest;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "extraction.mode", havingValue = "classifier")
public class GrpcLedgerTurnAdapter implements LedgerTurnPort {

    private final LedgerTurnServiceGrpc.LedgerTurnServiceBlockingStub stub;
    private final LedgerTurnProperties properties;

    public GrpcLedgerTurnAdapter(
            LedgerTurnServiceGrpc.LedgerTurnServiceBlockingStub stub, LedgerTurnProperties properties) {
        this.stub = stub;
        this.properties = properties;
    }

    @Override
    public void fileExpense(CategoryChoice category, ExpenseToFile expense) {
        FileExpenseProposalRequest.Builder request = FileExpenseProposalRequest.newBuilder()
                .setGrouping(category.grouping())
                .setCategory(category.category())
                .setDescription(expense.description())
                .setAmount(expense.amount())
                .setCurrencyCode(expense.currency().code());
        expense.merchant().ifPresent(request::setMerchant);
        call(() -> boundedStub().fileExpenseProposal(request.build()));
    }

    @Override
    public void recordPeriod(LocalDate from, LocalDate to) {
        RecordSpendingPeriodRequest request = RecordSpendingPeriodRequest.newBuilder()
                .setFrom(from.toString())
                .setTo(to.toString())
                .build();
        call(() -> boundedStub().recordSpendingPeriod(request));
    }

    private LedgerTurnServiceGrpc.LedgerTurnServiceBlockingStub boundedStub() {
        return stub.withDeadlineAfter(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    private void call(Runnable ledgerCall) {
        try {
            ledgerCall.run();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                throw new LedgerCallRefusedException("Ledger refused the call: " + e.getStatus(), e);
            }
            throw new ExpenseRecordingFailedException("Ledger call failed: " + e.getStatus(), e);
        }
    }
}
