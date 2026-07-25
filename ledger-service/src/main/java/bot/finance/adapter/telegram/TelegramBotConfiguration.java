package bot.finance.adapter.telegram;

import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.LoggerFactory;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Framework configuration for the Telegram adapter: the Bot API client, the inbound listener, and the
 * long-polling subscriber that drives them.
 */
@Configuration
@EnableConfigurationProperties(TelegramBotProperties.class)
public class TelegramBotConfiguration {

    @Bean
    TelegramBot telegramBot(TelegramBotProperties properties) {
        if (properties.polling().enabled() && (properties.token() == null || properties.token().isBlank())) {
            throw new IllegalStateException("""
                    telegram.bot.token is blank while telegram.bot.polling.enabled is true: set \
                    TELEGRAM_BOT_TOKEN, or set TELEGRAM_POLLING_ENABLED=false to boot without long polling""");
        }
        return new TelegramBot.Builder(properties.token())
                .apiUrl(properties.apiUrl())
                .updateListenerSleep(properties.polling().sleepMillis())
                .build();
    }

    @Bean
    TelegramUpdateListener telegramUpdateListener(HandleIncomingMessagePort handleIncomingMessagePort,
                                                  LoggerFactory loggerFactory) {
        return new TelegramUpdateListener(handleIncomingMessagePort, loggerFactory);
    }

    @Bean
    @ConditionalOnProperty(name = "telegram.bot.polling.enabled", havingValue = "true", matchIfMissing = true)
    TelegramLongPollingSubscriber telegramLongPollingSubscriber(TelegramBot bot,
                                                               UpdatesListener listener,
                                                               TelegramBotProperties properties,
                                                               LoggerFactory loggerFactory) {
        return new TelegramLongPollingSubscriber(bot, listener, properties, loggerFactory);
    }

}
