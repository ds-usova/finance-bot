package bot.finance.ai.adapter.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("ai.intent")
public record IntentExtractionProperties(Resource systemPrompt, Resource userMessageTemplate) {

}
