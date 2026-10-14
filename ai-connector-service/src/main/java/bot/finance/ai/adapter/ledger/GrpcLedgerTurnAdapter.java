package bot.finance.ai.adapter.ledger;

import bot.finance.ai.application.port.LedgerTurnPort;
import bot.finance.ai.domain.value.CategoryChoice;
import bot.finance.ai.domain.value.ExpenseToFile;
import bot.finance.ledger.adapter.grpc.v1.LedgerTurnServiceGrpc;
import java.time.LocalDate;
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
        // stub-intent: calls FileExpenseProposal with the grouping, category, description, merchant (left unset
        // when absent), amount and currency, under the shorter of the timeout and the inbound call's deadline;
        // INVALID_ARGUMENT throws LedgerCallRefusedException, any other failure or a missed deadline throws
        // ExpenseRecordingFailedException
    }

    @Override
    public void recordPeriod(LocalDate from, LocalDate to) {
        // stub-intent: calls RecordSpendingPeriod with both days written YYYY-MM-DD, failing as fileExpense does
    }
}
