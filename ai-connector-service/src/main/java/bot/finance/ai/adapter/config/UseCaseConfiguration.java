package bot.finance.ai.adapter.config;

import bot.finance.ai.application.port.ExpenseRecordingPort;
import bot.finance.ai.application.port.ExtractIntentsPort;
import bot.finance.ai.application.port.LoggerFactory;
import bot.finance.ai.application.usecase.ExtractIntentsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfiguration {

    @Bean
    ExtractIntentsPort extractIntentsPort(ExpenseRecordingPort expenseRecordingPort, LoggerFactory loggerFactory) {
        return new ExtractIntentsUseCase(expenseRecordingPort, loggerFactory);
    }

}
