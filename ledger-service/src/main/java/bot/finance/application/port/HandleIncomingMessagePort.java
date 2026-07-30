package bot.finance.application.port;

import bot.finance.application.dto.HandleIncomingMessageCommand;

public interface HandleIncomingMessagePort {

    void handle(HandleIncomingMessageCommand command);
}
