package bot.finance.ai.adapter.ai;

import bot.finance.ai.adapter.grpc.CallerTokenUtils;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.value.CurrencyCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Component;

@Component
public class AiExpenseRecordingAdapter implements ExpenseRecordingPort {

    private static final String NO_ASSUMED_CURRENCY = "none — leave an amount with no currency unrecorded";

    private final ChatClient chatClient;
    private final ExpenseRecordingProperties expenseRecordingProperties;
    private final SyncMcpToolCallbackProvider ledgerToolCallbackProvider;
    private final Logger logger;

    public AiExpenseRecordingAdapter(
            ChatClient chatClient,
            ExpenseRecordingProperties expenseRecordingProperties,
            SyncMcpToolCallbackProvider ledgerToolCallbackProvider,
            LoggerFactory loggerFactory) {
        this.chatClient = chatClient;
        this.expenseRecordingProperties = expenseRecordingProperties;
        this.ledgerToolCallbackProvider = ledgerToolCallbackProvider;
        this.logger = loggerFactory.getLogger(AiExpenseRecordingAdapter.class);
    }

    @Override
    public void record(String text, List<String> knownCategoryLabels, Optional<CurrencyCode> assumedCurrency) {
        if (CallerTokenUtils.callerToken().isEmpty()) {
            throw new ExpenseRecordingFailedException("No caller token held for this turn");
        }

        String userMessage = new PromptTemplate(expenseRecordingProperties.userMessageTemplate())
                .render(Map.of(
                        "knownCategories", String.join(", ", knownCategoryLabels),
                        "assumedCurrency",
                                assumedCurrency.map(CurrencyCode::code).orElse(NO_ASSUMED_CURRENCY),
                        "text", text));

        try {
            String answer = chatClient
                    .prompt()
                    .user(userMessage)
                    .tools(ledgerToolCallbackProvider)
                    .call()
                    .content();
            logger.debug("model's final answer: {}", answer);
        } catch (RuntimeException e) {
            throw new ExpenseRecordingFailedException("Failed to record expenses via provider", e);
        }
    }
}
