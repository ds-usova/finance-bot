package bot.finance.ai.adapter.ai;

import bot.finance.ai.application.dto.RawIntent;
import bot.finance.ai.application.port.IntentInferencePort;
import bot.finance.ai.application.port.Logger;
import bot.finance.ai.application.port.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiIntentInferenceAdapter implements IntentInferencePort {

    private final ChatClient chatClient;
    private final IntentExtractionProperties intentExtractionProperties;
    private final Logger logger;

    public AiIntentInferenceAdapter(
            ChatClient chatClient, IntentExtractionProperties intentExtractionProperties, LoggerFactory loggerFactory) {
        this.chatClient = chatClient;
        this.intentExtractionProperties = intentExtractionProperties;
        this.logger = loggerFactory.getLogger(AiIntentInferenceAdapter.class);
    }

    @Override
    public List<RawIntent> infer(String text, List<String> knownCategories) {
        // renders the user-message template (intentExtractionProperties.userMessageTemplate()) with text
        // and knownCategories, calls chatClient with that user message and requests structured output
        // typed to ExtractedIntents, maps each ExtractedIntent onto a RawIntent verbatim with no parsing
        // or validation, and wraps a transport error, non-2xx response, or unparseable body into
        // IntentInferenceException so no Spring AI or HTTP-client exception escapes this boundary
        return null;
    }

}
