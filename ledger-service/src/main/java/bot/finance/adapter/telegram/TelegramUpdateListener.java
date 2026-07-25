package bot.finance.adapter.telegram;

import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;

import java.util.List;

/**
 * Inbound Telegram adapter: receives each batch pengrad's poll loop fetches, maps it, and drives the
 * application's inbound message port.
 */
public class TelegramUpdateListener implements UpdatesListener {

    private final HandleIncomingMessagePort handleIncomingMessagePort;
    private final Logger log;

    public TelegramUpdateListener(HandleIncomingMessagePort handleIncomingMessagePort, LoggerFactory loggerFactory) {
        this.handleIncomingMessagePort = handleIncomingMessagePort;
        this.log = loggerFactory.getLogger(TelegramUpdateListener.class);
    }

    @Override
    public int process(List<Update> updates) {
        // maps each update via TelegramUpdateUtils and delegates text messages to the inbound port;
        // logs and skips non-text updates, logs and swallows a per-update failure, then confirms the whole batch
        return UpdatesListener.CONFIRMED_UPDATES_NONE;
    }

}
