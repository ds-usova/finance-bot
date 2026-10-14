package bot.finance.ai.adapter.ai;

import bot.finance.ai.application.dto.SpendingReadOut;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.SpendingReadOutPort;
import bot.finance.ai.domain.value.CurrencyCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AiSpendingReadOutAdapter implements SpendingReadOutPort {

    private final ChatClient chatClient;
    private final ExpenseRecordingProperties expenseRecordingProperties;
    private final Logger logger;

    public AiSpendingReadOutAdapter(
            @Qualifier("spendingReadOutChatClient") ChatClient chatClient,
            ExpenseRecordingProperties expenseRecordingProperties,
            LoggerFactory loggerFactory) {
        this.chatClient = chatClient;
        this.expenseRecordingProperties = expenseRecordingProperties;
        this.logger = loggerFactory.getLogger(AiSpendingReadOutAdapter.class);
    }

    @Override
    public SpendingReadOut readOut(String text, LocalDate currentDate, Optional<CurrencyCode> assumedCurrency) {
        // stub-intent: asks the provider once, with a JSON-schema response format and no tools, for the expenses
        // and periods the message says, the instructions stating the request's day and the assumed currency;
        // reads the answer into a read-out, a null merchant staying absent, and throws
        // ExpenseRecordingFailedException when the provider fails, times out, answers outside the shape or writes
        // a period day that is not YYYY-MM-DD
        return new SpendingReadOut(List.of(), List.of());
    }
}
