package bot.finance.adapter.telegram;

import bot.finance.application.dto.ResolutionAcknowledgement;
import bot.finance.application.dto.TurnReport;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ReplyParameters;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.EditMessageReplyMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TelegramMessageDeliveryAdapter implements MessageDeliveryPort {

    private static final String SEND_MESSAGE = "sendMessage";
    private static final String ANSWER_CALLBACK_QUERY = "answerCallbackQuery";
    private static final String EDIT_MESSAGE_REPLY_MARKUP = "editMessageReplyMarkup";

    private final TelegramBot bot;
    private final Logger log;

    public TelegramMessageDeliveryAdapter(TelegramBot bot, LoggerFactory loggerFactory) {
        this.bot = bot;
        this.log = loggerFactory.getLogger(TelegramMessageDeliveryAdapter.class);
    }

    @Override
    public void deliver(TurnReport report) {
        if (report == null) {
            throw new InvalidIncomingMessageException("report must not be null");
        }

        SendMessage request = new SendMessage(report.conversationId(), TurnReportRenderer.render(report))
                .replyParameters(
                        new ReplyParameters(Integer.valueOf(report.inboundMessageId())).allowSendingWithoutReply(true));
        TurnReportRenderer.renderKeyboard(report).ifPresent(request::replyMarkup);

        execute(request, SEND_MESSAGE).ifPresent(failure -> {
            throw failure;
        });
    }

    @Override
    public void acknowledge(ResolutionAcknowledgement ack) {
        if (ack == null) {
            throw new InvalidIncomingMessageException("acknowledgement must not be null");
        }

        AnswerCallbackQuery answer =
                new AnswerCallbackQuery(ack.interactionId()).text(ResolutionAcknowledgementRenderer.render(ack));
        EditMessageReplyMarkup edit =
                new EditMessageReplyMarkup(ack.conversationId(), Integer.parseInt(ack.reportMessageId()));

        // the keyboard is cleared even when the answer failed, so a tapped report cannot be tapped twice
        Optional<MessageDeliveryFailedException> answerFailure = execute(answer, ANSWER_CALLBACK_QUERY);
        Optional<MessageDeliveryFailedException> editFailure = execute(edit, EDIT_MESSAGE_REPLY_MARKUP);

        answerFailure.or(() -> editFailure).ifPresent(failure -> {
            throw failure;
        });
    }

    private <T extends BaseRequest<T, R>, R extends BaseResponse> Optional<MessageDeliveryFailedException> execute(
            T request, String method) {
        R response;
        try {
            response = bot.execute(request);
        } catch (RuntimeException e) {
            return Optional.of(new MessageDeliveryFailedException(
                    "failed to send telegram %s: %s".formatted(method, e.getMessage()), e));
        }

        if (!response.isOk()) {
            log.error(
                    "telegram {} failed with error code {}: {}", method, response.errorCode(), response.description());
            return Optional.of(new MessageDeliveryFailedException("telegram %s failed with error code %d: %s"
                    .formatted(method, response.errorCode(), response.description())));
        }
        return Optional.empty();
    }
}
