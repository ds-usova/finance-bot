package bot.finance.adapter.telegram;

import bot.finance.application.dto.IncomingMessage;
import com.pengrad.telegrambot.model.Update;

import java.util.Optional;

/**
 * Maps Telegram updates onto the transport-agnostic inbound command. This is where a Telegram chat id — a
 * numeric, Telegram-specific fact — becomes the conversation id the core works with.
 */
public final class TelegramUpdateUtils {

    private TelegramUpdateUtils() {
        // private constructor to prevent instantiation
    }

    public static Optional<IncomingMessage> toIncomingMessage(Update update) {
        // maps a pengrad Update to the transport-agnostic command when it carries a non-blank text message
        // in a chat, rendering the numeric Telegram chat id as the conversation id;
        // returns empty for every other kind of update so the listener can skip it.
        // Checks every precondition BEFORE constructing IncomingMessage - the record throws
        // InvalidIncomingMessageException on invalid input, and a skippable update must return empty,
        // never surface as an exception
        return Optional.empty();
    }

}
