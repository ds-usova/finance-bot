package bot.finance.ai.adapter.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExpenseRecordingProperties.class)
public class ChatClientConfiguration {

    @Bean
    ChatClient chatClient(ChatClient.Builder chatClientBuilder, ExpenseRecordingProperties properties) {
        return chatClientBuilder.defaultSystem(properties.systemPrompt()).build();
    }

}
