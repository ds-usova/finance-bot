package bot.finance.adapter.telegram;

import bot.finance.application.dto.ProposalReport;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.MessageDeliveryPort;
import com.pengrad.telegrambot.TelegramBot;
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
        // TODO RI02: send a SendMessage(report.conversationId(), ProposalReportUtils.render(report)) through
        // bot, with replyParameters(new ReplyParameters(Integer.valueOf(report.inboundMessageId()))
        // .allowSendingWithoutReply(true)) set on the same request, and translate a non-OK response or a client
        // exception into MessageDeliveryFailedException. A null report throws InvalidIncomingMessageException
        // and sends nothing.
        throw new UnsupportedOperationException("deliver is not yet implemented");
    }
}
