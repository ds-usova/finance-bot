package bot.finance.adapter.config;

import bot.finance.application.port.CreateExpensePort;
import bot.finance.application.port.ExpenseRepository;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.application.usecase.CreateExpenseUseCase;
import bot.finance.application.usecase.HandleIncomingMessageUseCase;
import bot.finance.application.usecase.InitializeUserUseCase;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfiguration {

    @Bean
    HandleIncomingMessagePort handleIncomingMessagePort(LoggerFactory loggerFactory) {
        return new HandleIncomingMessageUseCase(loggerFactory);
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
}
