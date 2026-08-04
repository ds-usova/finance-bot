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
import com.pengrad.telegrambot.request.SendMessage;
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
        // TODO: attach the keyboard ProposalReportUtils.renderKeyboard(report) gives, when present

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
    public void acknowledge(ResolutionAcknowledgement ack) {}
}
