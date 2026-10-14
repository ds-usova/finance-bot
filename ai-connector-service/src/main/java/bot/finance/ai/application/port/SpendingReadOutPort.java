package bot.finance.ai.application.port;

import bot.finance.ai.application.dto.SpendingReadOut;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.value.CurrencyCode;
import java.time.LocalDate;
import java.util.Optional;

public interface SpendingReadOutPort {

    /** @throws ExpenseRecordingFailedException if the provider fails or answers outside the read-out shape */
    SpendingReadOut readOut(String text, LocalDate currentDate, Optional<CurrencyCode> assumedCurrency);
}
