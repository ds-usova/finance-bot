package bot.finance.adapter.telegram;

import com.pengrad.telegrambot.TelegramBot;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({TelegramBotProperties.class, TelegramLoginProperties.class})
public class TelegramBotConfiguration {

    @Bean
    TelegramBot telegramBot(TelegramBotProperties properties) {
        if (properties.polling().enabled()
                && (properties.token() == null || properties.token().isBlank())) {
            throw new IllegalStateException(
                    """
                    telegram.bot.token is blank while telegram.bot.polling.enabled is true: set \
                    TELEGRAM_BOT_TOKEN, or set TELEGRAM_POLLING_ENABLED=false to boot without long polling""");
        }
        return new TelegramBot.Builder(properties.token())
                .apiUrl(properties.apiUrl())
                .updateListenerSleep(properties.polling().sleepMillis())
                .build();
    }
}
