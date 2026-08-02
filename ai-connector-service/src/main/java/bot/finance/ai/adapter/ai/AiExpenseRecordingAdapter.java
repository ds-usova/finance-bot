package bot.finance.ai.adapter.ai;

import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.value.CurrencyCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class AiExpenseRecordingAdapter implements ExpenseRecordingPort {

    private final ChatClient chatClient;
    private final ExpenseRecordingProperties expenseRecordingProperties;
    private final Logger logger;

    public AiExpenseRecordingAdapter(
            ChatClient chatClient,
            ExpenseRecordingProperties expenseRecordingProperties,
            LoggerFactory loggerFactory
    ) {
        this.chatClient = chatClient;
        this.expenseRecordingProperties = expenseRecordingProperties;
        this.logger = loggerFactory.getLogger(AiExpenseRecordingAdapter.class);
    }

    @Override
    public void record(String text, List<String> knownCategoryLabels, Optional<CurrencyCode> assumedCurrency) {
        // Renders the user message from expenseRecordingProperties.userMessageTemplate() with the known category
        // labels, the assumed currency (or the "leave unrecorded" wording when absent) and the text; prompts the
        // chat client with the ledger's tool callbacks attached via .tools(provider); logs the model's final
        // answer at debug and discards it; translates any RuntimeException from the provider or the tool loop
        // into ExpenseRecordingFailedException.
    }

}
