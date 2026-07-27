package bot.finance.ai.adapter.ai;

import bot.finance.ai.application.dto.RawIntent;
import bot.finance.ai.application.port.IntentInferencePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.domain.exception.IntentInferenceException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AiIntentInferenceAdapter implements IntentInferencePort {

    private final ChatClient chatClient;
    private final IntentExtractionProperties intentExtractionProperties;
    private final Logger logger;

    public AiIntentInferenceAdapter(
            ChatClient chatClient,
            IntentExtractionProperties intentExtractionProperties,
            LoggerFactory loggerFactory
    ) {
        this.chatClient = chatClient;
        this.intentExtractionProperties = intentExtractionProperties;
        this.logger = loggerFactory.getLogger(AiIntentInferenceAdapter.class);
    }

    @Override
    public List<RawIntent> infer(String text, List<String> knownCategories) {
        String userMessage = new PromptTemplate(intentExtractionProperties.userMessageTemplate())
                .render(Map.of(
                        "text", text,
                        "knownCategories", String.join(", ", knownCategories)
                        )
                );

        logger.debug("rendered user message: {}", userMessage);

        ExtractedIntents extractedIntents;
        try {
            extractedIntents = chatClient.prompt().user(userMessage).call().entity(ExtractedIntents.class);
        } catch (RuntimeException e) {
            throw new IntentInferenceException("Failed to infer intents from provider", e);
        }

        if (extractedIntents == null) {
            throw new IntentInferenceException("Provider returned no content");
        }

        logger.debug("provider extracted intents: {}", extractedIntents);
        return extractedIntents.intents().stream().map(AiIntentInferenceAdapter::toRawIntent).toList();
    }

    private static RawIntent toRawIntent(ExtractedIntent extractedIntent) {
        return new RawIntent(
                extractedIntent.target(),
                extractedIntent.operation(),
                extractedIntent.categoryName(),
                extractedIntent.newCategoryName(),
                extractedIntent.amount(),
                extractedIntent.currency(),
                extractedIntent.description());
    }

}
