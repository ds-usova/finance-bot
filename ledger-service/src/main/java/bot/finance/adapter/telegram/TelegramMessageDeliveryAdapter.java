package bot.finance.adapter.telegram;

import bot.finance.application.dto.ProposalReport;
import bot.finance.application.dto.ResolutionAcknowledgement;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import bot.finance.domain.exception.InvalidIncomingMessageException;
import bot.finance.domain.exception.MessageDeliveryFailedException;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.request.ReplyParameters;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.EditMessageReplyMarkup;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import org.springframework.stereotype.Component;

@Component
public class TelegramMessageDeliveryAdapter implements MessageDeliveryPort {

    private final TelegramBot bot;
    private final Logger log;

    public TelegramMessageDeliveryAdapter(TelegramBot bot, LoggerFactory loggerFactory) {
        this.bot = bot;
        this.log = loggerFactory.getLogger(TelegramMessageDeliveryAdapter.class);
    }

    @Override
    public void deliver(ProposalReport report) {
        if (report == null) {
            throw new InvalidIncomingMessageException("report must not be null");
        }

        SendMessage request = new SendMessage(report.conversationId(), ProposalReportUtils.render(report))
                .replyParameters(
                        new ReplyParameters(Integer.valueOf(report.inboundMessageId())).allowSendingWithoutReply(true));
        ProposalReportUtils.renderKeyboard(report).ifPresent(request::replyMarkup);

        SendResponse response;
        try {
            response = bot.execute(request);
        } catch (RuntimeException e) {
            throw new MessageDeliveryFailedException("failed to send telegram message: " + e.getMessage(), e);
        }

        if (!response.isOk()) {
            log.error(
                    "telegram sendMessage failed with error code {}: {}", response.errorCode(), response.description());
            throw new MessageDeliveryFailedException("telegram sendMessage failed with error code %d: %s"
                    .formatted(response.errorCode(), response.description()));
        }
    }

    // sends AnswerCallbackQuery with the wording, then EditMessageReplyMarkup with no markup, attempting the
    // second even when the first failed and throwing the first failure
    @Override
    public void acknowledge(ResolutionAcknowledgement ack) {
        if (ack == null) {
            throw new InvalidIncomingMessageException("acknowledgement must not be null");
        }

        MessageDeliveryFailedException answerFailure = answerCallbackQuery(ack);
        MessageDeliveryFailedException editFailure = editMessageReplyMarkup(ack);

        if (answerFailure != null) {
            throw answerFailure;
        }
        if (editFailure != null) {
            throw editFailure;
        }
    }

    private MessageDeliveryFailedException answerCallbackQuery(ResolutionAcknowledgement ack) {
        AnswerCallbackQuery request =
                new AnswerCallbackQuery(ack.interactionId()).text(ResolutionAcknowledgementUtils.render(ack));

        BaseResponse response;
        try {
            response = bot.execute(request);
        } catch (RuntimeException e) {
            return new MessageDeliveryFailedException("failed to answer telegram callback query: " + e.getMessage(), e);
        }

        if (!response.isOk()) {
            log.error(
                    "telegram answerCallbackQuery failed with error code {}: {}",
                    response.errorCode(),
                    response.description());
            return new MessageDeliveryFailedException("telegram answerCallbackQuery failed with error code %d: %s"
                    .formatted(response.errorCode(), response.description()));
        }
        return null;
    }

    private MessageDeliveryFailedException editMessageReplyMarkup(ResolutionAcknowledgement ack) {
        EditMessageReplyMarkup request =
                new EditMessageReplyMarkup(ack.conversationId(), Integer.parseInt(ack.reportMessageId()));

        BaseResponse response;
        try {
            response = bot.execute(request);
        } catch (RuntimeException e) {
            return new MessageDeliveryFailedException(
                    "failed to edit telegram message reply markup: " + e.getMessage(), e);
        }

        if (!response.isOk()) {
            log.error(
                    "telegram editMessageReplyMarkup failed with error code {}: {}",
                    response.errorCode(),
                    response.description());
            return new MessageDeliveryFailedException("telegram editMessageReplyMarkup failed with error code %d: %s"
                    .formatted(response.errorCode(), response.description()));
        }
        return null;
    }
}
