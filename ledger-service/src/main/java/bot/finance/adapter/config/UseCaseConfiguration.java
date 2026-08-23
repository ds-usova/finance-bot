package bot.finance.adapter.config;

import bot.finance.application.port.AcceptExpensesPort;
import bot.finance.application.port.BrowseCategoriesPort;
import bot.finance.application.port.BrowseExpensesPort;
import bot.finance.application.port.BrowseGroupingsPort;
import bot.finance.application.port.CategoryRepository;
import bot.finance.application.port.ChangeExpenseCategoryPort;
import bot.finance.application.port.ClearEmptiedReportsPort;
import bot.finance.application.port.CreateExpensePort;
import bot.finance.application.port.CreateExpenseProposalPort;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.GroupingRepository;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.IntentExtractionPort;
import bot.finance.application.port.ListCategoriesPort;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.application.port.ProposalReportRepository;
import bot.finance.application.port.ReadSessionPort;
import bot.finance.application.port.ReportClearingDispatchPort;
import bot.finance.application.port.ResolveProposalsPort;
import bot.finance.application.port.SpendingQueryRepository;
import bot.finance.application.port.SummarizeSpendingPort;
import bot.finance.application.port.TurnMeters;
import bot.finance.application.port.UserRepository;
import bot.finance.application.usecase.AcceptExpensesUseCase;
import bot.finance.application.usecase.BrowseCategoriesUseCase;
import bot.finance.application.usecase.BrowseExpensesUseCase;
import bot.finance.application.usecase.BrowseGroupingsUseCase;
import bot.finance.application.usecase.ChangeExpenseCategoryUseCase;
import bot.finance.application.usecase.ClearEmptiedReportsUseCase;
import bot.finance.application.usecase.CreateExpenseProposalUseCase;
import bot.finance.application.usecase.CreateExpenseUseCase;
import bot.finance.application.usecase.HandleIncomingMessageUseCase;
import bot.finance.application.usecase.InitializeUserUseCase;
import bot.finance.application.usecase.ListCategoriesUseCase;
import bot.finance.application.usecase.ReadSessionUseCase;
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
            MessageDeliveryPort messageDeliveryPort,
            SpendingQueryRepository spendingQueryRepository,
            ExpenseRepository expenseRepository,
            ProposalReportRepository proposalReportRepository,
            TurnMeters turnMeters,
            LoggerFactory loggerFactory) {
        return new HandleIncomingMessageUseCase(
                initializeUserPort,
                groupingRepository,
                intentExtractionPort,
                messageDeliveryPort,
                Clock.systemUTC(),
                spendingQueryRepository,
                expenseRepository,
                proposalReportRepository,
                turnMeters,
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
            ExpenseRepository expenseRepository,
            LoggerFactory loggerFactory) {
        return new CreateExpenseProposalUseCase(
                userRepository,
                groupingRepository,
                categoryRepository,
                expenseRepository,
                Clock.systemUTC(),
                loggerFactory);
    }

    @Bean
    ResolveProposalsPort resolveProposalsPort(
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            MessageDeliveryPort messageDeliveryPort,
            TurnMeters turnMeters,
            LoggerFactory loggerFactory) {
        return new ResolveProposalsUseCase(
                userRepository, expenseRepository, messageDeliveryPort, Clock.systemUTC(), turnMeters, loggerFactory);
    }

    @Bean
    ListCategoriesPort listCategoriesPort(
            UserRepository userRepository,
            GroupingRepository groupingRepository,
            CategoryRepository categoryRepository) {
        return new ListCategoriesUseCase(userRepository, groupingRepository, categoryRepository);
    }

    @Bean
    BrowseExpensesPort browseExpensesPort(
            UserRepository userRepository, ExpenseRepository expenseRepository, LoggerFactory loggerFactory) {
        return new BrowseExpensesUseCase(userRepository, expenseRepository, loggerFactory);
    }

    @Bean
    BrowseCategoriesPort browseCategoriesPort(UserRepository userRepository, CategoryRepository categoryRepository) {
        return new BrowseCategoriesUseCase(userRepository, categoryRepository);
    }

    @Bean
    BrowseGroupingsPort browseGroupingsPort(UserRepository userRepository, GroupingRepository groupingRepository) {
        return new BrowseGroupingsUseCase(userRepository, groupingRepository);
    }

    @Bean
    AcceptExpensesPort acceptExpensesPort(
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            ReportClearingDispatchPort reportClearingDispatchPort,
            LoggerFactory loggerFactory) {
        return new AcceptExpensesUseCase(
                userRepository, expenseRepository, reportClearingDispatchPort, Clock.systemUTC(), loggerFactory);
    }

    @Bean
    ChangeExpenseCategoryPort changeExpenseCategoryPort(
            UserRepository userRepository,
            CategoryRepository categoryRepository,
            ExpenseRepository expenseRepository,
            LoggerFactory loggerFactory) {
        return new ChangeExpenseCategoryUseCase(
                userRepository, categoryRepository, expenseRepository, Clock.systemUTC(), loggerFactory);
    }

    @Bean
    ReadSessionPort readSessionPort(UserRepository userRepository) {
        return new ReadSessionUseCase(userRepository);
    }

    @Bean
    ClearEmptiedReportsPort clearEmptiedReportsPort(
            ExpenseRepository expenseRepository,
            ProposalReportRepository proposalReportRepository,
            MessageDeliveryPort messageDeliveryPort,
            LoggerFactory loggerFactory) {
        return new ClearEmptiedReportsUseCase(
                expenseRepository, proposalReportRepository, messageDeliveryPort, loggerFactory);
    }
}
