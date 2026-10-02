package bot.finance.adapter.web;

import bot.finance.api.model.FileWrittenMessageRequest;
import bot.finance.api.model.ListWrittenMessages200ResponseInner;
import bot.finance.application.dto.BrowseWrittenMessagesCommand;
import bot.finance.application.dto.FileWrittenMessageCommand;
import bot.finance.application.dto.ReadWrittenMessageCommand;
import bot.finance.application.dto.WrittenMessageAnswer;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.domain.value.AuthenticatedUserId;
import java.util.List;

public final class WrittenMessageWebMapper {

    private WrittenMessageWebMapper() {}

    public static FileWrittenMessageCommand toFileWrittenMessageCommand(
            FileWrittenMessageRequest request, AuthenticatedUserId userId) {
        return new FileWrittenMessageCommand(userId, request.getText());
    }

    public static ReadWrittenMessageCommand toReadWrittenMessageCommand(Long id, AuthenticatedUserId userId) {
        return new ReadWrittenMessageCommand(userId, id);
    }

    public static BrowseWrittenMessagesCommand toBrowseWrittenMessagesCommand(
            String state, AuthenticatedUserId userId) {
        // stub-intent: refuse any state but READING with InvalidWrittenMessageException naming `state`, then build
        // the command over the caller.
        return null;
    }

    public static ListWrittenMessages200ResponseInner toResponse(WrittenMessageView view) {
        // stub-intent: the message's id, state name, text and outcome (absent while unsettled), with no days.
        return null;
    }

    public static ListWrittenMessages200ResponseInner toResponse(WrittenMessageAnswer answer) {
        // stub-intent: the answered message as toResponse(WrittenMessageView) maps it, carrying its days.
        return null;
    }

    public static List<ListWrittenMessages200ResponseInner> toResponses(List<WrittenMessageView> views) {
        // stub-intent: each view mapped as toResponse(WrittenMessageView) maps it, in order.
        return List.of();
    }
}
