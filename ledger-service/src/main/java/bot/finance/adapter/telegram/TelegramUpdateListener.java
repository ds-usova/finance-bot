package bot.finance.adapter.telegram;

import bot.finance.application.dto.HandleIncomingMessageCommand;
import bot.finance.application.dto.ResolveProposalsCommand;
import bot.finance.application.port.HandleIncomingMessagePort;
import bot.finance.application.port.Logger;
import bot.finance.application.port.LoggerFactory;
import bot.finance.application.port.ResolveProposalsPort;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TelegramUpdateListener implements UpdatesListener {

    private final HandleIncomingMessagePort handleIncomingMessagePort;
    private final ResolveProposalsPort resolveProposalsPort;
    private final Logger log;

    public TelegramUpdateListener(
            HandleIncomingMessagePort handleIncomingMessagePort,
            ResolveProposalsPort resolveProposalsPort,
            LoggerFactory loggerFactory) {
        this.handleIncomingMessagePort = handleIncomingMessagePort;
        this.resolveProposalsPort = resolveProposalsPort;
        this.log = loggerFactory.getLogger(TelegramUpdateListener.class);
    }

    @Override
    public int process(List<Update> updates) {
        for (Update update : updates) {
            handle(update);
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    /**
     * A failure is swallowed because {@link #process} confirms the batch either way: rethrowing would stall the
     * poll loop on one bad update.
     */
    private void handle(Update update) {
        try {
            Optional<HandleIncomingMessageCommand> command =
                    TelegramUpdateMapper.toHandleIncomingMessageCommand(update);
            if (command.isPresent()) {
                handleIncomingMessagePort.handle(command.get());
                return;
            }

            Optional<ResolveProposalsCommand> resolution = TelegramUpdateMapper.toResolveProposalsCommand(update);
            if (resolution.isPresent()) {
                resolveProposalsPort.resolve(resolution.get());
                return;
            }

            log.debug("skipping unrecognised telegram update {}", update.updateId());
        } catch (RuntimeException e) {
            log.error("failed to handle telegram update {}", update.updateId(), e);
        }
    }
}
