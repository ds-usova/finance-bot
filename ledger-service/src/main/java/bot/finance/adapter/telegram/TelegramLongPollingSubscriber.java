package bot.finance.adapter.telegram;

import bot.finance.adapter.telegram.TelegramBotProperties.Polling;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.TelegramException;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.request.GetUpdates;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "telegram.bot.polling.enabled", havingValue = "true", matchIfMissing = true)
public class TelegramLongPollingSubscriber implements SmartLifecycle {

    private static final String MESSAGE_UPDATES = "message";

    private final TelegramBot bot;
    private final UpdatesListener listener;
    private final TelegramBotProperties properties;
    private final Logger log;

    private volatile boolean running;

    public TelegramLongPollingSubscriber(
            TelegramBot bot, UpdatesListener listener, TelegramBotProperties properties, LoggerFactory loggerFactory) {
        this.bot = bot;
        this.listener = listener;
        this.properties = properties;
        this.log = loggerFactory.getLogger(TelegramLongPollingSubscriber.class);
    }

    @Override
    public void start() {
        Polling polling = properties.polling();
        GetUpdates request = new GetUpdates()
                .limit(polling.limit())
                .timeout(polling.timeoutSeconds())
                .allowedUpdates(MESSAGE_UPDATES);

        bot.setUpdatesListener(listener, this::logPollFailure, request);
        running = true;
        log.debug(
                "telegram long polling started with limit {} and timeout {}s",
                polling.limit(),
                polling.timeoutSeconds());
    }

    @Override
    public void stop() {
        bot.removeGetUpdatesListener();
        running = false;
        log.debug("telegram long polling stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Pengrad polls again once this returns, so a failure must not be rethrown — that would end the loop.
     */
    private void logPollFailure(TelegramException exception) {
        log.error("telegram getUpdates polling failed: {}", exception.getMessage());
    }
}
