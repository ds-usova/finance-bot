package bot.finance.ai.application.port;

import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.value.CurrencyCode;
import java.util.List;
import java.util.Optional;

public interface ExpenseRecordingPort {

    /**
     * @throws ExpenseRecordingFailedException if the provider or the ledger cannot be reached, or the provider's
     *                                          answer cannot be read
     */
    void record(
            String text,
            List<String> categoryGroupings,
            String catchAllGrouping,
            Optional<CurrencyCode> assumedCurrency);
}
