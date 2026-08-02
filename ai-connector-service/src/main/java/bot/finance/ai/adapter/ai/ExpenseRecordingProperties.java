package bot.finance.ai.adapter.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("ai.expense")
public record ExpenseRecordingProperties(Resource systemPrompt, Resource userMessageTemplate) {

}
