package bot.finance.adapter.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The client appends {@code token} to {@code apiUrl}, so every Bot API call carries the token in its path.
 */
@ConfigurationProperties("telegram.bot")
public record TelegramBotProperties(String token, String apiUrl, Polling polling) {

    /**
     * @param enabled        whether the application long-polls for updates at all
     * @param limit          the maximum number of updates a single {@code getUpdates} call may return
     * @param timeoutSeconds how long Telegram holds an empty {@code getUpdates} call open
     * @param sleepMillis    how long the poll loop waits between calls
     */
    public record Polling(boolean enabled, int limit, int timeoutSeconds, long sleepMillis) { }

}
