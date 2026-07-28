package bot.finance.adapter.config;

import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.InitializeUserPort;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.UserRepository;
import bot.finance.application.usecase.HandleIncomingMessageUseCase;
import bot.finance.application.usecase.InitializeUserUseCase;
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

}
