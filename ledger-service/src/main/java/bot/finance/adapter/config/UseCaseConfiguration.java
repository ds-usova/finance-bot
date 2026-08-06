package bot.finance.adapter.config;

import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.CreateExpensePort;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.ExpenseProposalRepository;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.ListCategoriesPort;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ResolveProposalsPort;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.application.port.UserRepository;
import bot.finance.application.usecase.CreateExpenseProposalUseCase;
import bot.finance.application.usecase.CreateExpenseUseCase;
import bot.finance.application.usecase.HandleIncomingMessageUseCase;
import bot.finance.application.usecase.InitializeUserUseCase;
import bot.finance.application.usecase.ListCategoriesUseCase;
import bot.finance.application.usecase.ResolveProposalsUseCase;
import bot.finance.application.usecase.SummarizeSpendingUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfiguration {

    @Bean
    HandleIncomingMessagePort handleIncomingMessagePort(
            InitializeUserPort initializeUserPort,
            GroupingRepository groupingRepository,
            IntentExtractionPort intentExtractionPort,
            ExpenseProposalRepository expenseProposalRepository,
            MessageDeliveryPort messageDeliveryPort,
            SpendingQueryRepository spendingQueryRepository,
            ExpenseRepository expenseRepository,
            LoggerFactory loggerFactory) {
        return new HandleIncomingMessageUseCase(
                initializeUserPort,
                groupingRepository,
                intentExtractionPort,
                expenseProposalRepository,
                messageDeliveryPort,
                Clock.systemUTC(),
                spendingQueryRepository,
                expenseRepository,
                loggerFactory);
    }

    @Bean
    SummarizeSpendingPort summarizeSpendingPort(
            UserRepository userRepository, SpendingQueryRepository spendingQueryRepository) {
        return new SummarizeSpendingUseCase(userRepository, spendingQueryRepository, Clock.systemUTC());
    }

    @Bean
    InitializeUserPort initializeUserPort(UserRepository userRepository, LoggerFactory loggerFactory) {
        return new InitializeUserUseCase(userRepository, loggerFactory);
    }

    @Bean
    CreateExpensePort createExpensePort(
            UserRepository userRepository, ExpenseRepository expenseRepository, LoggerFactory loggerFactory) {
        return new CreateExpenseUseCase(userRepository, expenseRepository, Clock.systemUTC(), loggerFactory);
    }

    @Bean
    CreateExpenseProposalPort createExpenseProposalPort(
            UserRepository userRepository,
            GroupingRepository groupingRepository,
            CategoryRepository categoryRepository,
            ExpenseProposalRepository expenseProposalRepository,
            LoggerFactory loggerFactory) {
        return new CreateExpenseProposalUseCase(
                userRepository,
                groupingRepository,
                categoryRepository,
                expenseProposalRepository,
                Clock.systemUTC(),
                loggerFactory);
    }

    @Bean
    ResolveProposalsPort resolveProposalsPort(
            UserRepository userRepository,
            ExpenseProposalRepository expenseProposalRepository,
            ExpenseRepository expenseRepository,
            MessageDeliveryPort messageDeliveryPort,
            LoggerFactory loggerFactory) {
        return new ResolveProposalsUseCase(
                userRepository,
                expenseProposalRepository,
                expenseRepository,
                messageDeliveryPort,
                Clock.systemUTC(),
                loggerFactory);
    }

    @Bean
    ListCategoriesPort listCategoriesPort(
            UserRepository userRepository,
            GroupingRepository groupingRepository,
            CategoryRepository categoryRepository) {
        return new ListCategoriesUseCase(userRepository, groupingRepository, categoryRepository);
    }
}
