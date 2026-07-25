package bot.finance.adapter.telegram;

import bot.finance.application.dto.IncomingMessage;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
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

    /**
     * Maps an update onto the inbound command when it carries a non-blank text message in a chat, rendering the
     * numeric Telegram chat id as the conversation id. Every other kind of update yields an empty
     * {@code Optional} so the listener can skip it — which is why each precondition is checked here rather than
     * left to {@link IncomingMessage}'s own validation: a skippable update must never surface as an exception.
     *
     * @param update the update to map, possibly {@code null}
     * @return the command, or empty when the update carries nothing to act on
     */
    public static Optional<IncomingMessage> toIncomingMessage(Update update) {
        if (update == null) {
            return Optional.empty();
        }
        Message message = update.message();
        if (message == null) {
            return Optional.empty();
        }
        String text = message.text();
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        Chat chat = message.chat();
        if (chat == null) {
            return Optional.empty();
        }
        return Optional.of(new IncomingMessage(String.valueOf(chat.id()), text));
    }

}
