package bot.finance.adapter.web;

import bot.finance.api.model.FileWrittenMessageRequest;
import bot.finance.api.model.ListWrittenMessages200ResponseInner;
import bot.finance.api.model.WrittenMessageOutcome;
import bot.finance.api.model.WrittenMessageState;
import bot.finance.application.dto.BrowseWrittenMessagesCommand;
import bot.finance.application.dto.FileWrittenMessageCommand;
import bot.finance.application.dto.ReadWrittenMessageCommand;
import bot.finance.application.dto.WrittenMessageAnswer;
import bot.finance.application.dto.WrittenMessageView;
import bot.finance.domain.exception.InvalidWrittenMessageException;
import bot.finance.domain.value.AuthenticatedUserId;
import bot.finance.domain.value.WrittenMessageStatus;
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
        if (!"READING".equals(state)) {
            throw new InvalidWrittenMessageException("unsupported written message state: " + state);
        }
        return new BrowseWrittenMessagesCommand(userId);
    }

    public static ListWrittenMessages200ResponseInner toResponse(WrittenMessageView view) {
        WrittenMessageState state =
                view.status() == WrittenMessageStatus.DONE ? WrittenMessageState.DONE : WrittenMessageState.READING;
        WrittenMessageOutcome outcome =
                view.outcome().map(o -> WrittenMessageOutcome.valueOf(o.name())).orElse(null);
        return new ListWrittenMessages200ResponseInner()
                .id(view.id())
                .state(state)
                .text(view.text())
                .outcome(outcome);
    }

    public static ListWrittenMessages200ResponseInner toResponse(WrittenMessageAnswer answer) {
        return toResponse(answer.message()).days(answer.days());
    }

    public static List<ListWrittenMessages200ResponseInner> toResponses(List<WrittenMessageView> views) {
        return views.stream().map(WrittenMessageWebMapper::toResponse).toList();
    }
}
