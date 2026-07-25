package bot.finance.adapter.telegram;

import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import org.springframework.context.SmartLifecycle;

/**
 * Binds the Telegram long-polling loop to the application lifecycle: the loop starts with the context and stops
 * with it.
 *
 * <p>The listener is taken as the {@link UpdatesListener} interface rather than the concrete adapter, so a test
 * can substitute a recording fake.
 */
public class TelegramLongPollingSubscriber implements SmartLifecycle {

    private final TelegramBot bot;
    private final UpdatesListener listener;
    private final TelegramBotProperties properties;
    private final Logger log;

    public TelegramLongPollingSubscriber(TelegramBot bot,
                                        UpdatesListener listener,
                                        TelegramBotProperties properties,
                                        LoggerFactory loggerFactory) {
        this.bot = bot;
        this.listener = listener;
        this.properties = properties;
        this.log = loggerFactory.getLogger(TelegramLongPollingSubscriber.class);
    }

    @Override
    public void start() {
        // registers the listener on the TelegramBot with a GetUpdates request built from the polling properties
        // (limit, timeout, allowed_updates=message), and an ExceptionHandler that logs getUpdates failures
        // without killing the loop
    }

    @Override
    public void stop() {
        // removes the getUpdates listener so the poll loop stops
    }

    @Override
    public boolean isRunning() {
        // reports whether the poll loop is currently registered
        return false;
    }

}
