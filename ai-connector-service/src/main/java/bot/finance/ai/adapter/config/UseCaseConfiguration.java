package bot.finance.ai.adapter.config;

import bot.finance.ai.adapter.scheduling.MemoryProperties;
import bot.finance.ai.adapter.typesafe.TypesafeClassifierProperties;
import bot.finance.ai.application.port.BackfillEmbeddingsPort;
import bot.finance.ai.application.port.CategoryClassifierPort;
import bot.finance.ai.application.port.ChangeAttemptStorePort;
import bot.finance.ai.application.port.ChangeStreamMeters;
import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.LearnMessageOutcomePort;
import bot.finance.ai.application.port.LedgerTurnPort;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.port.MessageEmbeddingPort;
import bot.finance.ai.application.port.MessageMemoryPort;
import bot.finance.ai.application.port.MessageStorePort;
import bot.finance.ai.application.port.PurgeMessagesPort;
import bot.finance.ai.application.port.RecallExamplesPort;
import bot.finance.ai.application.port.RecordedExpenseStorePort;
import bot.finance.ai.application.port.SpendingReadOutPort;
import bot.finance.ai.application.port.TurnMeters;
import bot.finance.ai.application.usecase.BackfillEmbeddingsUseCase;
import bot.finance.ai.application.usecase.ClassifierTurn;
import bot.finance.ai.application.usecase.ExtractIntentsUseCase;
import bot.finance.ai.application.usecase.ExtractionTurn;
import bot.finance.ai.application.usecase.LearnMessageOutcomeUseCase;
import bot.finance.ai.application.usecase.MessageEmbedder;
import bot.finance.ai.application.usecase.PurgeMessagesUseCase;
import bot.finance.ai.application.usecase.RecallExamplesUseCase;
import bot.finance.ai.application.usecase.ToolCallingTurn;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExtractionProperties.class)
public class UseCaseConfiguration {

    // Injected so that a mode the properties refuse fails startup here, before the turn beans below go missing.
    UseCaseConfiguration(ExtractionProperties extractionProperties) {}

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ExtractIntentsPort extractIntentsPort(
            ExtractionTurn extractionTurn,
            TurnMeters turnMeters,
            MessageStorePort messageStorePort,
            RecallExamplesPort recallExamplesPort,
            LoggerFactory loggerFactory) {
        return new ExtractIntentsUseCase(
                extractionTurn, turnMeters, messageStorePort, recallExamplesPort, loggerFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "extraction.mode", havingValue = "tools", matchIfMissing = true)
    ExtractionTurn toolCallingTurn(ExpenseRecordingPort expenseRecordingPort) {
        return new ToolCallingTurn(expenseRecordingPort);
    }

    @Bean
    @ConditionalOnProperty(name = "extraction.mode", havingValue = "classifier")
    ExtractionTurn classifierTurn(
            SpendingReadOutPort spendingReadOutPort,
            CategoryClassifierPort categoryClassifierPort,
            LedgerTurnPort ledgerTurnPort,
            TurnMeters turnMeters,
            Clock clock,
            TypesafeClassifierProperties classifierProperties,
            LoggerFactory loggerFactory) {
        return new ClassifierTurn(
                spendingReadOutPort,
                categoryClassifierPort,
                ledgerTurnPort,
                turnMeters,
                clock,
                classifierProperties.minConfidence(),
                loggerFactory);
    }

    @Bean
    MessageEmbedder messageEmbedder(
            MessageMemoryPort messageMemoryPort,
            MessageEmbeddingPort messageEmbeddingPort,
            MemoryProperties properties,
            LoggerFactory loggerFactory) {
        return new MessageEmbedder(
                messageMemoryPort, messageEmbeddingPort, properties.embeddingAttempts(), loggerFactory);
    }

    @Bean
    RecallExamplesPort recallExamplesPort(
            MessageMemoryPort messageMemoryPort,
            MessageEmbedder messageEmbedder,
            MemoryProperties properties,
            LoggerFactory loggerFactory) {
        return new RecallExamplesUseCase(
                messageMemoryPort,
                messageEmbedder,
                properties.examples(),
                properties.minSimilarity(),
                properties.recentWindow(),
                properties.maxAge(),
                properties.exampleLines(),
                loggerFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "memory.enabled", havingValue = "true")
    BackfillEmbeddingsPort backfillEmbeddingsPort(
            MessageMemoryPort messageMemoryPort,
            MessageEmbedder messageEmbedder,
            MemoryProperties properties,
            LoggerFactory loggerFactory) {
        return new BackfillEmbeddingsUseCase(
                messageMemoryPort,
                messageEmbedder,
                properties.backfillBatch(),
                properties.backfillBatches(),
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
            ChangeStreamMeters changeStreamMeters,
            LoggerFactory loggerFactory) {
        return new LearnMessageOutcomeUseCase(
                recordedExpenseStorePort,
                changeAttemptStorePort,
                properties.entryAttempts(),
                changeStreamMeters,
                loggerFactory);
    }
}
