package bot.finance.adapter.config;

import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.usecase.HandleIncomingMessageUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the plain use-case classes as beans. Use cases belong to no single adapter, so their configuration
 * lives here rather than in an adapter subpackage.
 */
@Configuration
public class UseCaseConfiguration {

    @Bean
    HandleIncomingMessagePort handleIncomingMessagePort(LoggerFactory loggerFactory) {
        return new HandleIncomingMessageUseCase(loggerFactory);
    }

}
