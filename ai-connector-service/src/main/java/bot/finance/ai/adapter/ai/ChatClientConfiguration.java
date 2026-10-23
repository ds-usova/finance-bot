package bot.finance.ai.adapter.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@EnableConfigurationProperties(ExpenseRecordingProperties.class)
public class ChatClientConfiguration {

    @Bean
    @Primary
    ChatClient chatClient(ChatClient.Builder chatClientBuilder, ExpenseRecordingProperties properties) {
        return chatClientBuilder.defaultSystem(properties.systemPrompt()).build();
    }

    @Bean
    ChatClient spendingReadOutChatClient(ChatClient.Builder chatClientBuilder, ExpenseRecordingProperties properties) {
        return chatClientBuilder
                .clone()
                .defaultSystem(properties.readOutSystemPrompt())
                .build();
    }
}
