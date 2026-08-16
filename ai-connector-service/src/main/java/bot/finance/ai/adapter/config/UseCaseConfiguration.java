package bot.finance.ai.adapter.config;

import bot.finance.ai.adapter.scheduling.MemoryProperties;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.application.port.PurgeMessagesPort;
import bot.finance.ai.application.usecase.ExtractIntentsUseCase;
import bot.finance.ai.application.usecase.PurgeMessagesUseCase;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfiguration {

    @Bean
    ExtractIntentsPort extractIntentsPort(
            ExpenseRecordingPort expenseRecordingPort, MessageStorePort messageStorePort, LoggerFactory loggerFactory) {
        return new ExtractIntentsUseCase(expenseRecordingPort, messageStorePort, loggerFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
    PurgeMessagesPort purgeMessagesPort(
            MessageStorePort messageStorePort, MemoryProperties properties, Clock clock, LoggerFactory loggerFactory) {
        return new PurgeMessagesUseCase(
                messageStorePort, clock, properties.maxAge(), properties.purgeBatch(), loggerFactory);
    }
}
