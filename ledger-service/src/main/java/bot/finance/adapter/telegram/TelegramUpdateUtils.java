package bot.finance.adapter.telegram;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import java.util.Optional;

public final class TelegramUpdateUtils {

    private TelegramUpdateUtils() {}

    /**
     * @param update the update to map, possibly {@code null}
     * @return the command, or empty for an update the listener should skip
     */
    public static Optional<HandleIncomingMessageCommand> toHandleIncomingMessageCommand(Update update) {
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
        // TODO RU05/D16: read message.from().id() for userExternalId, and return Optional.empty() when from is
        // absent, exactly as this method already does for a missing chat or text.
        String conversationId = String.valueOf(chat.id());
        String inboundMessageId = String.valueOf(message.messageId());
        return Optional.of(new HandleIncomingMessageCommand(conversationId, conversationId, inboundMessageId, text));
    }
}
