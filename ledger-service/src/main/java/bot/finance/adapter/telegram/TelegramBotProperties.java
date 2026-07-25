package bot.finance.adapter.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Telegram Bot API client, bound from the {@code telegram.bot} block.
 *
 * @param token   the bot token, which is part of every Bot API URL
 * @param apiUrl  the Bot API base URL the token is appended to; pointed at a stub server in tests
 * @param polling the long-polling settings
 */
@ConfigurationProperties("telegram.bot")
public record TelegramBotProperties(String token, String apiUrl, Polling polling) {

    /**
     * @param enabled        whether the application long-polls for updates at all
     * @param limit          the maximum number of updates a single {@code getUpdates} call may return
     * @param timeoutSeconds how long Telegram holds an empty {@code getUpdates} call open
     * @param sleepMillis    how long the poll loop waits between calls
     */
    public record Polling(boolean enabled, int limit, int timeoutSeconds, long sleepMillis) {
    }

}
