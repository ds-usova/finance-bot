package bot.finance.ai.adapter.ai;

import bot.finance.ai.application.dto.ReadOutExpense;
import bot.finance.ai.application.dto.ReadOutPeriod;
import bot.finance.ai.application.dto.SpendingReadOut;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.SpendingReadOutPort;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.value.CurrencyCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.chat.client.AdvisorParams;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AiSpendingReadOutAdapter implements SpendingReadOutPort {

    private static final String NO_ASSUMED_CURRENCY = "none — give an amount with no stated currency a null currency";

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
        String userMessage = new PromptTemplate(expenseRecordingProperties.readOutMessageTemplate())
                .render(Map.of(
                        "today",
                        currentDate.toString(),
                        "assumedCurrency",
                        assumedCurrency.map(CurrencyCode::code).orElse(NO_ASSUMED_CURRENCY),
                        "text",
                        text));

        try {
            ReadOutAnswer answer = chatClient
                    .prompt()
                    .user(userMessage)
                    .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
                    .call()
                    .entity(ReadOutAnswer.class);
            logger.debug("model's read-out: {}", answer);
            return answer.toReadOut();
        } catch (RuntimeException e) {
            throw new ExpenseRecordingFailedException("Failed to read out spending via provider", e);
        }
    }

    private record ReadOutAnswer(List<ReadOutExpense> expenses, List<AnsweredPeriod> periods) {

        SpendingReadOut toReadOut() {
            return new SpendingReadOut(
                    expenses, periods.stream().map(AnsweredPeriod::toPeriod).toList());
        }
    }

    private record AnsweredPeriod(String from, String to) {

        ReadOutPeriod toPeriod() {
            return new ReadOutPeriod(LocalDate.parse(from), LocalDate.parse(to));
        }
    }
}
