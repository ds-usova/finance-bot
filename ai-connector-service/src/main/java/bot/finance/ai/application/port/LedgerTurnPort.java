package bot.finance.ai.application.port;

import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.exception.LedgerCallRefusedException;
import bot.finance.ai.domain.value.CategoryChoice;
import bot.finance.ai.domain.value.ExpenseToFile;
import java.time.LocalDate;

public interface LedgerTurnPort {

    /**
     * @throws LedgerCallRefusedException if the ledger refuses this one call
     * @throws ExpenseRecordingFailedException if the ledger answers any other failure, or not in time
     */
    void fileExpense(CategoryChoice category, ExpenseToFile expense);

    /** Throws as {@link #fileExpense}. */
    void recordPeriod(LocalDate from, LocalDate to);
}
