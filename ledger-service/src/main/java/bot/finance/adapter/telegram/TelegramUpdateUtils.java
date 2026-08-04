package bot.finance.adapter.telegram;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.ResolveProposalsCommand;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.message.MaybeInaccessibleMessage;
import java.util.Optional;

public final class TelegramUpdateUtils {

    private TelegramUpdateUtils() {}

    public static Optional<ResolveProposalsCommand> toResolveProposalsCommand(Update update) {
        if (update == null) {
            return Optional.empty();
        }
        CallbackQuery callbackQuery = update.callbackQuery();
        if (callbackQuery == null) {
            return Optional.empty();
        }
        User from = callbackQuery.from();
        if (from == null) {
            return Optional.empty();
        }
        MaybeInaccessibleMessage message = callbackQuery.maybeInaccessibleMessage();
        if (message == null) {
            return Optional.empty();
        }
        Chat chat = message.chat();
        if (chat == null) {
            return Optional.empty();
        }
        Optional<ProposalCallbackData.ParsedCallback> parsed = ProposalCallbackData.parse(callbackQuery.data());
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        String userExternalId = String.valueOf(from.id());
        String conversationId = String.valueOf(chat.id());
        String reportMessageId = String.valueOf(message.messageId());
        return Optional.of(new ResolveProposalsCommand(
                userExternalId,
                conversationId,
                reportMessageId,
                callbackQuery.id(),
                parsed.get().reference(),
                parsed.get().resolution()));
    }

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
        User from = message.from();
        if (from == null) {
            return Optional.empty();
        }
        String userExternalId = String.valueOf(from.id());
        String conversationId = String.valueOf(chat.id());
        String inboundMessageId = String.valueOf(message.messageId());
        return Optional.of(new HandleIncomingMessageCommand(userExternalId, conversationId, inboundMessageId, text));
    }
}
