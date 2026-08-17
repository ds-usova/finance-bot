package bot.finance.ai.adapter.config;

import bot.finance.ai.adapter.scheduling.MemoryProperties;
import bot.finance.ai.application.port.BackfillEmbeddingsPort;
import bot.finance.ai.application.port.ChangeAttemptStorePort;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.LearnMessageOutcomePort;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageEmbeddingPort;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.application.port.PurgeMessagesPort;
import bot.finance.ai.application.port.RecallExamplesPort;
import bot.finance.ai.application.port.RecordedExpenseStorePort;
import bot.finance.ai.application.usecase.BackfillEmbeddingsUseCase;
import bot.finance.ai.application.usecase.ExtractIntentsUseCase;
import bot.finance.ai.application.usecase.LearnMessageOutcomeUseCase;
import bot.finance.ai.application.usecase.PurgeMessagesUseCase;
import bot.finance.ai.application.usecase.RecallExamplesUseCase;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfiguration {

    @Bean
    ExtractIntentsPort extractIntentsPort(
            ExpenseRecordingPort expenseRecordingPort,
            MessageStorePort messageStorePort,
            RecallExamplesPort recallExamplesPort,
            LoggerFactory loggerFactory) {
        return new ExtractIntentsUseCase(expenseRecordingPort, messageStorePort, recallExamplesPort, loggerFactory);
    }

    @Bean
    RecallExamplesPort recallExamplesPort(
            MessageMemoryPort messageMemoryPort,
            MessageEmbeddingPort messageEmbeddingPort,
            MemoryProperties properties,
            LoggerFactory loggerFactory) {
        return new RecallExamplesUseCase(
                messageMemoryPort,
                messageEmbeddingPort,
                properties.examples(),
                properties.minSimilarity(),
                properties.recentWindow(),
                properties.maxAge(),
                properties.exampleLines(),
                properties.embeddingAttempts(),
                loggerFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
    BackfillEmbeddingsPort backfillEmbeddingsPort(
            MessageMemoryPort messageMemoryPort,
            MessageEmbeddingPort messageEmbeddingPort,
            MemoryProperties properties,
            LoggerFactory loggerFactory) {
        return new BackfillEmbeddingsUseCase(
                messageMemoryPort,
                messageEmbeddingPort,
                properties.backfillBatch(),
                properties.backfillBatches(),
                properties.embeddingAttempts(),
                properties.purgeInterval(),
                loggerFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
    PurgeMessagesPort purgeMessagesPort(
            MessageStorePort messageStorePort, MemoryProperties properties, Clock clock, LoggerFactory loggerFactory) {
        return new PurgeMessagesUseCase(
                messageStorePort, clock, properties.maxAge(), properties.purgeBatch(), loggerFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
    LearnMessageOutcomePort learnMessageOutcomePort(
            RecordedExpenseStorePort recordedExpenseStorePort,
            ChangeAttemptStorePort changeAttemptStorePort,
            MemoryProperties properties,
            LoggerFactory loggerFactory) {
        return new LearnMessageOutcomeUseCase(
                recordedExpenseStorePort, changeAttemptStorePort, properties.entryAttempts(), loggerFactory);
    }
}
