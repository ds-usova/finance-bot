package bot.finance.ai.adapter.ai;

import bot.finance.ai.adapter.grpc.CallerTokenContext;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.TurnMeters;
import bot.finance.ai.domain.exception.ExpenseRecordingFailedException;
import bot.finance.ai.domain.value.CatalogueEntry;
import bot.finance.ai.domain.value.CurrencyCode;
import bot.finance.ai.domain.value.MessageExample;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class AiExpenseRecordingAdapter implements ExpenseRecordingPort {

    private static final String NO_ASSUMED_CURRENCY = "none — leave an amount with no currency unrecorded";
    private static final String EXAMPLES_HEADING = "How this person's earlier messages were recorded — accepted "
            + "means they confirmed it, discarded means they rejected it:";

    private final ChatClient chatClient;
    private final ExpenseRecordingProperties expenseRecordingProperties;
    private final ExampleSectionRenderer exampleSectionRenderer;
    private final SyncMcpToolCallbackProvider ledgerToolCallbackProvider;
    private final TurnMeters turnMeters;
    private final Logger logger;

    public AiExpenseRecordingAdapter(
            ChatClient chatClient,
            ExpenseRecordingProperties expenseRecordingProperties,
            ExampleSectionRenderer exampleSectionRenderer,
            SyncMcpToolCallbackProvider ledgerToolCallbackProvider,
            TurnMeters turnMeters,
            LoggerFactory loggerFactory) {
        this.chatClient = chatClient;
        this.expenseRecordingProperties = expenseRecordingProperties;
        this.exampleSectionRenderer = exampleSectionRenderer;
        this.ledgerToolCallbackProvider = ledgerToolCallbackProvider;
        this.turnMeters = turnMeters;
        this.logger = loggerFactory.getLogger(AiExpenseRecordingAdapter.class);
    }

    @Override
    public void record(
            String text,
            List<CatalogueEntry> catalogue,
            String catchAllGrouping,
            Optional<CurrencyCode> assumedCurrency,
            LocalDate currentDate,
            Optional<List<MessageExample>> examples) {
        if (CallerTokenContext.callerToken().isEmpty()) {
            throw new ExpenseRecordingFailedException("No caller token held for this turn");
        }

        String userMessage = new PromptTemplate(expenseRecordingProperties.userMessageTemplate())
                .render(Map.of(
                        "categoryGroupings",
                        catalogue.stream().map(CatalogueEntry::grouping).collect(Collectors.joining(", ")),
                        "catchAllGrouping",
                        catchAllGrouping,
                        "assumedCurrency",
                        assumedCurrency.map(CurrencyCode::code).orElse(NO_ASSUMED_CURRENCY),
                        "text",
                        text,
                        "today",
                        currentDate.toString(),
                        "examples",
                        renderExamplesSection(examples)));

        try {
            String answer = chatClient
                    .prompt()
                    .user(userMessage)
                    .toolCallbacks(turnTools(catalogue))
                    .call()
                    .content();
            logger.debug("model's final answer: {}", answer);
        } catch (RuntimeException e) {
            throw new ExpenseRecordingFailedException("Failed to record expenses via provider", e);
        }
    }

    private List<ToolCallback> turnTools(List<CatalogueEntry> catalogue) {
        ToolCallback lookup = new CategoryLookupToolCallback(catalogue, turnMeters);
        String lookupName = lookup.getToolDefinition().name();
        return Stream.concat(
                        Arrays.stream(ledgerToolCallbackProvider.getToolCallbacks())
                                .filter(callback -> !lookupName.equals(
                                        callback.getToolDefinition().name())),
                        Stream.of(lookup))
                .toList();
    }

    private String renderExamplesSection(Optional<List<MessageExample>> examples) {
        String rendered = exampleSectionRenderer.render(examples);
        if (rendered.isEmpty()) {
            return "";
        }
        return EXAMPLES_HEADING + "\n" + rendered + "\n\n";
    }
}
