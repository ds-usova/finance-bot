package bot.finance.adapter.telegram;

import bot.finance.application.dto.IncomingMessage;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Inbound Telegram adapter: receives each batch pengrad's poll loop fetches, maps it, and drives the
 * application's inbound message port.
 */
@Component
public class TelegramUpdateListener implements UpdatesListener {

    private final HandleIncomingMessagePort handleIncomingMessagePort;
    private final Logger log;

    public TelegramUpdateListener(HandleIncomingMessagePort handleIncomingMessagePort, LoggerFactory loggerFactory) {
        this.handleIncomingMessagePort = handleIncomingMessagePort;
        this.log = loggerFactory.getLogger(TelegramUpdateListener.class);
    }

    @Override
    public int process(List<Update> updates) {
        for (Update update : updates) {
            handle(update);
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    /**
     * Delegates one update to the inbound port, skipping anything that carries no text message and swallowing a
     * failure so a single bad update cannot stall the poll loop — the batch is confirmed either way.
     *
     * @param update the update to handle
     */
    private void handle(Update update) {
        Optional<IncomingMessage> message = TelegramUpdateUtils.toIncomingMessage(update);
        if (message.isEmpty()) {
            log.debug("skipping non-text telegram update {}", update.updateId());
            return;
        }

        try {
            handleIncomingMessagePort.handle(message.get());
        } catch (RuntimeException e) {
            log.error("failed to handle telegram update {}", update.updateId(), e);
        }
    }

}
