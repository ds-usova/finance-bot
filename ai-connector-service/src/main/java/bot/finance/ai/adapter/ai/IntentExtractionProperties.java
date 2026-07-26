package bot.finance.ai.adapter.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Binds {@code ai.intent.system-prompt} and {@code ai.intent.user-message-template}, both
 * {@code classpath:} resource locations.
 */
@ConfigurationProperties("ai.intent")
public record IntentExtractionProperties(Resource systemPrompt, Resource userMessageTemplate) {

}
